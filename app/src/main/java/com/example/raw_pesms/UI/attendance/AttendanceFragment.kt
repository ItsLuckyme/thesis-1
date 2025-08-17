package com.example.raw_pesms.UI.attendance

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.raw_pesms.R
import com.example.raw_pesms.UI.AttendanceAdapter
import com.example.raw_pesms.data.AI.FaceRecognitionManager
import com.example.raw_pesms.data.model.AttendanceStatus
import com.example.raw_pesms.data.model.Student
import com.example.raw_pesms.data.model.SMSNotificationManager
import com.example.raw_pesms.viewmodel.AttendanceViewModel
import com.example.raw_pesms.viewmodel.AttendanceViewModelFactory
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class AttendanceFragment : Fragment() {

    companion object {
        fun newInstance(grade: String?, section: String?): AttendanceFragment {
            return AttendanceFragment().apply {
                arguments = Bundle().apply {
                    putString("grade", grade)
                    putString("section", section)
                }
            }
        }
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnSave: Button
    private lateinit var btnCamera: ImageButton
    private lateinit var adapter: AttendanceAdapter
    private var progressBar: ProgressBar? = null

    private var grade: String? = null
    private var section: String? = null
    private var saveTimeoutHandler: Handler? = null
    private var saveTimeoutRunnable: Runnable? = null
    private var isSaving = false
    private var isRecognitionInProgress = false

    private lateinit var smsManager: SMSNotificationManager

    private val viewModel: AttendanceViewModel by activityViewModels {
        AttendanceViewModelFactory(requireActivity().application)
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) startFaceRecognition()
        else Toast.makeText(
            requireContext(),
            "Camera permission is required for face recognition",
            Toast.LENGTH_LONG
        ).show()
    }

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(
                requireContext(),
                "SMS permission granted. Notifications will be sent to guardians.",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                requireContext(),
                "SMS permission denied. Guardian notifications will not be sent.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val takePicture = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        handleFaceRecognitionResult(bitmap)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            grade = it.getString("grade")
            section = it.getString("section")
        }
        Log.d("AttendanceFragment", "onCreate: grade=$grade, section=$section")
        saveTimeoutHandler = Handler(Looper.getMainLooper())
        smsManager = SMSNotificationManager(requireContext())

        if (!smsManager.checkSmsPermission()) {
            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
        }

        Log.d("AttendanceFragment", "AttendanceFragment created successfully")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        Log.d("AttendanceFragment", "onCreateView called")
        val view = inflater.inflate(R.layout.fragment_attendance, container, false)
        recyclerView = view.findViewById(R.id.recyclerViewAttendance)
        btnSave = view.findViewById(R.id.btnSaveAttendance)
        btnCamera = view.findViewById(R.id.btnCamera)
        progressBar = view.findViewById(R.id.progressBar) ?: run {
            ProgressBar(requireContext()).apply {
                visibility = View.GONE
                isIndeterminate = true
            }
        }
        Log.d("AttendanceFragment", "Views initialized successfully")
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        adapter = AttendanceAdapter(
            onStatusChanged = { student, status ->
                val mapped = when (status.lowercase()) {
                    "present" -> AttendanceStatus.PRESENT
                    "late" -> AttendanceStatus.LATE
                    else -> AttendanceStatus.ABSENT
                }
                grade?.let { g ->
                    section?.let { s ->
                        lifecycleScope.launch {
                            try {
                                val guardianPhone = student.guardianPhone ?: "09561955224"
                                sendAttendanceSMSToNumber(student, g, s, guardianPhone, mapped)
                            } catch (e: Exception) {
                                Log.e("AttendanceFragment", "Failed to send SMS for ${student.firstName}", e)
                            }
                        }
                    }
                }
                viewModel.markAttendance(student.id, mapped)
            },
            onDeleteClicked = { student ->
                grade?.let { g ->
                    section?.let { s ->
                        viewModel.deleteStudent(g, s, student.id) {
                            Toast.makeText(
                                requireContext(),
                                "Deleted ${student.firstName}",
                                Toast.LENGTH_SHORT
                            ).show()
                            viewModel.loadStudents(g, s)
                        }
                    }
                }
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun setupObservers() {
        grade?.let { g ->
            section?.let { s ->
                viewModel.loadStudents(g, s)
            }
        }
        viewModel.students.observe(viewLifecycleOwner) { students ->
            adapter.submitList(students)
        }
        viewModel.saveStatus.observe(viewLifecycleOwner) { isSuccess ->
            if (isSuccess != null) {
                cancelSaveTimeout()
                isSaving = false
                if (isSuccess) {
                    Toast.makeText(
                        requireContext(),
                        "Attendance saved successfully!",
                        Toast.LENGTH_SHORT
                    ).show()
                    navigateToHistory()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Failed to save attendance. Please try again.",
                        Toast.LENGTH_LONG
                    ).show()
                    resetSaveButton()
                }
                viewModel.clearSaveStatus()
            }
        }
        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMsg ->
            if (!errorMsg.isNullOrEmpty()) {
                cancelSaveTimeout()
                isSaving = false
                Toast.makeText(requireContext(), "Error: $errorMsg", Toast.LENGTH_LONG).show()
                resetSaveButton()
                viewModel.clearErrorMessage()
            }
        }
    }

    private fun setupClickListeners() {
        btnSave.setOnClickListener {
            if (isSaving) {
                Toast.makeText(requireContext(), "Save already in progress...", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            if (grade == null || section == null) {
                Toast.makeText(requireContext(), "Grade/Section missing", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.students.value?.takeIf { it.isNotEmpty() }?.let { studentsList ->
                isSaving = true
                btnSave.isEnabled = false
                btnSave.text = "Saving..."
                setupSaveTimeout()
                grade?.let { g ->
                    section?.let { s -> viewModel.saveAttendanceForClass(g, s) }
                }
            } ?: run {
                Toast.makeText(
                    requireContext(),
                    "No students to save attendance for",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        btnCamera.setOnClickListener {
            if (isRecognitionInProgress) {
                Toast.makeText(
                    requireContext(),
                    "Face recognition in progress...",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            val students = viewModel.students.value
            val enrolledStudents = students?.filter { !it.faceEmbedding.isNullOrEmpty() }
            when {
                students.isNullOrEmpty() ->
                    Toast.makeText(
                        requireContext(),
                        "No students found in this class",
                        Toast.LENGTH_SHORT
                    ).show()
                enrolledStudents.isNullOrEmpty() ->
                    Toast.makeText(
                        requireContext(),
                        "No students have enrolled faces for recognition",
                        Toast.LENGTH_LONG
                    ).show()
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED ->
                    startFaceRecognition()
                else ->
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startFaceRecognition() {
        Toast.makeText(
            requireContext(),
            "📸 Position students in the camera frame for recognition",
            Toast.LENGTH_LONG
        ).show()
        try {
            takePicture.launch(null)
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Error opening camera: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun handleFaceRecognitionResult(bitmap: Bitmap?) {
        if (bitmap == null) {
            Toast.makeText(
                requireContext(),
                "No image captured. Please try again.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val students = viewModel.students.value
        when {
            students.isNullOrEmpty() ->
                Toast.makeText(requireContext(), "No students found", Toast.LENGTH_SHORT).show()
            students.none { !it.faceEmbedding.isNullOrEmpty() } ->
                Toast.makeText(
                    requireContext(),
                    "No enrolled faces found for recognition",
                    Toast.LENGTH_SHORT
                ).show()
            else -> proceedWithRecognition(bitmap)
        }
    }

    private fun proceedWithRecognition(bitmap: Bitmap) {
        isRecognitionInProgress = true
        btnCamera.isEnabled = false
        progressBar?.visibility = View.VISIBLE

        val faceRecognitionManager = try {
            FaceRecognitionManager(requireContext())
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Face recognition setup failed: ${e.message}.",
                Toast.LENGTH_LONG
            ).show()
            resetRecognitionUI()
            return
        }

        lifecycleScope.launch {
            try {
                val detectedFaces = faceRecognitionManager.detectFaces(bitmap)
                if (detectedFaces.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        "No faces detected in the image. Please try again with better lighting.",
                        Toast.LENGTH_LONG
                    ).show()
                    resetRecognitionUI()
                    return@launch
                }

                val recognizedStudents = mutableListOf<Student>()
                val threshold = 0.6f
                val enrolled = viewModel.students.value!!.filter { !it.faceEmbedding.isNullOrEmpty() }

                detectedFaces.forEach { detectedFace ->
                    faceRecognitionManager.embed(detectedFace)?.let { detectedEmbedding ->
                        var bestMatch: Student? = null
                        var bestSimilarity = 0f
                        enrolled.forEach { student ->
                            student.faceEmbedding?.split(",")?.mapNotNull { it.toFloatOrNull() }
                                ?.toFloatArray()?.let { storedEmbedding ->
                                    if (storedEmbedding.size == detectedEmbedding.size) {
                                        val similarity = calculateCosineSimilarity(
                                            detectedEmbedding,
                                            storedEmbedding
                                        )
                                        if (similarity > bestSimilarity && similarity > threshold) {
                                            bestSimilarity = similarity
                                            bestMatch = student
                                        }
                                    }
                                }
                        }
                        bestMatch?.takeIf { !recognizedStudents.contains(it) }?.also {
                            recognizedStudents.add(it)
                        }
                    }
                }

                val allStudents = viewModel.students.value ?: emptyList()

                if (recognizedStudents.isNotEmpty()) {
                    recognizedStudents.forEach { student ->
                        viewModel.markAttendance(student.id, AttendanceStatus.PRESENT)
                        grade?.let { g ->
                            section?.let { s ->
                                lifecycleScope.launch {
                                    val guardianPhone = student.guardianPhone ?: "09561955224"
                                    sendAttendanceSMSToNumber(student, g, s, guardianPhone, AttendanceStatus.PRESENT)
                                }
                            }
                        }
                    }

                    val absentStudents = allStudents.filterNot { recognizedStudents.contains(it) }
                    absentStudents.forEach { student ->
                        viewModel.markAttendance(student.id, AttendanceStatus.ABSENT)
                        grade?.let { g ->
                            section?.let { s ->
                                lifecycleScope.launch {
                                    val guardianPhone = student.guardianPhone ?: "09561955224"
                                    sendAttendanceSMSToNumber(student, g, s, guardianPhone, AttendanceStatus.ABSENT)
                                }
                            }
                        }
                    }

                    val names = recognizedStudents.joinToString(", ") { "${it.firstName} ${it.lastName}" }
                    Toast.makeText(
                        requireContext(),
                        "✅ Recognized: $names\nAbsent marked for others.",
                        Toast.LENGTH_LONG
                    ).show()

                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!isSaving && grade != null && section != null) {
                            viewModel.students.value?.takeIf { it.isNotEmpty() }
                                ?.let {
                                    isSaving = true
                                    btnSave.isEnabled = false
                                    btnSave.text = "Saving..."
                                    setupSaveTimeout()
                                    viewModel.saveAttendanceForClass(grade!!, section!!)
                                }
                        }
                    }, 1000)

                } else {
                    Toast.makeText(
                        requireContext(),
                        "❌ No enrolled students recognized in the image.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Error during face recognition: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                resetRecognitionUI()
            }
        }
    }

    private fun calculateCosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        if (embedding1.size != embedding2.size) return 0f
        var dot = 0f
        var norm1 = 0f
        var norm2 = 0f
        embedding1.indices.forEach {
            dot += embedding1[it] * embedding2[it]
            norm1 += embedding1[it] * embedding1[it]
            norm2 += embedding2[it] * embedding2[it]
        }
        val mag = sqrt(norm1) * sqrt(norm2)
        return if (mag > 0f) dot / mag else 0f
    }

    private fun resetRecognitionUI() {
        isRecognitionInProgress = false
        btnCamera.isEnabled = true
        progressBar?.visibility = View.GONE
    }

    private fun setupSaveTimeout() {
        cancelSaveTimeout()
        saveTimeoutRunnable = Runnable {
            if (isSaving) {
                Toast.makeText(
                    requireContext(),
                    "Save operation timed out. Please check your connection and try again.",
                    Toast.LENGTH_LONG
                ).show()
                isSaving = false
                resetSaveButton()
                viewModel.clearSaveStatus()
                viewModel.clearErrorMessage()
            }
        }
        saveTimeoutHandler?.postDelayed(saveTimeoutRunnable!!, 30_000)
    }

    private fun cancelSaveTimeout() {
        saveTimeoutRunnable?.let {
            saveTimeoutHandler?.removeCallbacks(it)
            saveTimeoutRunnable = null
        }
    }

    private fun resetSaveButton() {
        btnSave.isEnabled = true
        btnSave.text = getString(R.string.save_attendance)
    }

    private fun navigateToHistory() {
        try {
            findNavController().navigate(
                R.id.action_attendanceFragment_to_attendanceHistoryFragment,
                Bundle().apply {
                    putString("grade", grade)
                    putString("section", section)
                    putString("actionType", "history")
                }
            )
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Navigation error: ${e.message}", Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun sendAttendanceSMSToNumber(student: Student, grade: String, section: String, phoneNumber: String, status: AttendanceStatus) {
        try {
            val statusText = when (status) {
                AttendanceStatus.PRESENT -> "PRESENT"
                AttendanceStatus.LATE -> "LATE"
                AttendanceStatus.ABSENT -> "ABSENT"
            }

            val message = "ATTENDANCE UPDATE: ${student.firstName} ${student.lastName} from Grade $grade - Section $section is marked as $statusText."

            if (smsManager.checkSmsPermission()) {
                val smsManager = android.telephony.SmsManager.getDefault()
                smsManager.sendTextMessage(
                    phoneNumber,
                    null,
                    message,
                    null,
                    null
                )
            }
        } catch (e: Exception) {
            Log.e("AttendanceFragment", "Failed to send SMS to $phoneNumber", e)
            throw e
        }
    }
}
