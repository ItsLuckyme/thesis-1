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
        progressBar = view.findViewById<ProgressBar>(R.id.progressBar) ?: run {
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
        Log.d("AttendanceFragment", "onViewCreated called")
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        Log.d("AttendanceFragment", "Setup completed successfully")
    }

    private fun setupRecyclerView() {
        Log.d("AttendanceFragment", "Setting up RecyclerView")
        adapter = AttendanceAdapter(
            onStatusChanged = { student, status ->
                val mapped = when (status.lowercase()) {
                    "present" -> AttendanceStatus.PRESENT
                    "late" -> AttendanceStatus.LATE
                    else -> AttendanceStatus.ABSENT
                }
                viewModel.markAttendance(student.id, mapped)
                Log.d("AttendanceFragment", "Marked ${student.firstName} as $mapped")
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
        Log.d("AttendanceFragment", "RecyclerView setup completed")
    }

    private fun setupObservers() {
        Log.d("AttendanceFragment", "Setting up observers")
        grade?.let { g ->
            section?.let { s ->
                Log.d("AttendanceFragment", "Loading students for grade: $g, section: $s")
                viewModel.loadStudents(g, s)
            }
        }

        viewModel.students.observe(viewLifecycleOwner) { students ->
            Log.d("AttendanceFragment", "Students loaded: ${students.size}")
            adapter.submitList(students)
        }

        viewModel.saveStatus.observe(viewLifecycleOwner) { isSuccess ->
            Log.d("AttendanceFragment", "Save status received: $isSuccess")
            if (isSuccess != null) {
                cancelSaveTimeout()
                isSaving = false
                if (isSuccess) {
                    Log.d("AttendanceFragment", "Attendance saved successfully")
                    Toast.makeText(requireContext(), "Attendance saved successfully!", Toast.LENGTH_SHORT).show()
                    navigateToHistory()
                } else {
                    Log.e("AttendanceFragment", "Failed to save attendance")
                    Toast.makeText(requireContext(), "Failed to save attendance. Please try again.", Toast.LENGTH_LONG).show()
                    resetSaveButton()
                }
                viewModel.clearSaveStatus()
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMsg ->
            if (!errorMsg.isNullOrEmpty()) {
                Log.e("AttendanceFragment", "Error received: $errorMsg")
                cancelSaveTimeout()
                isSaving = false
                Toast.makeText(requireContext(), "Error: $errorMsg", Toast.LENGTH_LONG).show()
                resetSaveButton()
                viewModel.clearErrorMessage()
            }
        }
        Log.d("AttendanceFragment", "Observers setup completed")
    }

    private fun setupClickListeners() {
        Log.d("AttendanceFragment", "Setting up click listeners")
        btnSave.setOnClickListener {
            if (isSaving) {
                Toast.makeText(requireContext(), "Save already in progress...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (grade == null || section == null) {
                Toast.makeText(requireContext(), "Grade/Section missing", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.students.value?.takeIf { it.isNotEmpty() }?.let { studentsList ->
                Log.d("AttendanceFragment", "Starting save process for $grade $section with ${studentsList.size} students")
                isSaving = true
                btnSave.isEnabled = false
                btnSave.text = "Saving..."
                setupSaveTimeout()
                grade?.let { g ->
                    section?.let { s -> viewModel.saveAttendanceForClass(g, s) }
                }
            } ?: run {
                Toast.makeText(requireContext(), "No students to save attendance for", Toast.LENGTH_SHORT).show()
            }
        }

        btnCamera.setOnClickListener {
            if (isRecognitionInProgress) {
                Toast.makeText(requireContext(), "Face recognition in progress...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val students = viewModel.students.value
            val enrolledStudents = students?.filter { !it.faceEmbedding.isNullOrEmpty() }
            when {
                students.isNullOrEmpty() ->
                    Toast.makeText(requireContext(), "No students found in this class", Toast.LENGTH_SHORT).show()
                enrolledStudents.isNullOrEmpty() ->
                    Toast.makeText(requireContext(), "No students have enrolled faces for recognition", Toast.LENGTH_LONG).show()
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED ->
                    startFaceRecognition()
                else ->
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
        Log.d("AttendanceFragment", "Click listeners setup completed")
    }

    private fun startFaceRecognition() {
        Toast.makeText(requireContext(), "📸 Position students in the camera frame for recognition", Toast.LENGTH_LONG).show()
        try {
            takePicture.launch(null)
        } catch (e: Exception) {
            Log.e("AttendanceFragment", "Error opening camera", e)
            Toast.makeText(requireContext(), "Error opening camera: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleFaceRecognitionResult(bitmap: Bitmap?) {
        if (bitmap == null) {
            Toast.makeText(requireContext(), "No image captured. Please try again.", Toast.LENGTH_SHORT).show()
            return
        }

        val students = viewModel.students.value
        when {
            students.isNullOrEmpty() ->
                Toast.makeText(requireContext(), "No students found", Toast.LENGTH_SHORT).show()
            students.none { !it.faceEmbedding.isNullOrEmpty() } ->
                Toast.makeText(requireContext(), "No enrolled faces found for recognition", Toast.LENGTH_SHORT).show()
            else -> proceedWithRecognition(bitmap)
        }
    }

    private fun proceedWithRecognition(bitmap: Bitmap) {
        isRecognitionInProgress = true
        btnCamera.isEnabled = false
        progressBar?.visibility = View.VISIBLE
        val faceRecognitionManager = FaceRecognitionManager(requireContext())

        lifecycleScope.launch {
            try {
                Log.d("AttendanceFragment", "Starting face recognition process")
                val detectedFaces = faceRecognitionManager.detectFaces(bitmap)
                if (detectedFaces.isEmpty()) {
                    Toast.makeText(requireContext(), "No faces detected in the image. Please try again with better lighting.", Toast.LENGTH_LONG).show()
                    resetRecognitionUI()
                    return@launch
                }
                Log.d("AttendanceFragment", "Detected ${detectedFaces.size} face(s)")

                val recognizedStudents = mutableListOf<Student>()
                val threshold = 0.6f

                val enrolled = viewModel.students.value!!.filter { !it.faceEmbedding.isNullOrEmpty() }
                detectedFaces.forEach { detectedFace ->
                    faceRecognitionManager.embed(detectedFace)?.let { detectedEmbedding ->
                        var bestMatch: Student? = null
                        var bestSimilarity = 0f
                        enrolled.forEach { student ->
                            student.faceEmbedding?.split(",")?.mapNotNull { it.toFloatOrNull() }?.toFloatArray()?.let { storedEmbedding ->
                                if (storedEmbedding.size == detectedEmbedding.size) {
                                    val similarity = calculateCosineSimilarity(detectedEmbedding, storedEmbedding)
                                    Log.d("AttendanceFragment", "Similarity with ${student.firstName}: $similarity")
                                    if (similarity > bestSimilarity && similarity > threshold) {
                                        bestSimilarity = similarity
                                        bestMatch = student
                                    }
                                }
                            }
                        }
                        bestMatch?.takeIf { !recognizedStudents.contains(it) }?.also { recognizedStudents.add(it); Log.d("AttendanceFragment", "Recognized student: ${it.firstName} ${it.lastName} (similarity: $bestSimilarity)") }
                    }
                }

                if (recognizedStudents.isNotEmpty()) {
                    recognizedStudents.forEach { viewModel.markAttendance(it.id, AttendanceStatus.PRESENT) }
                    val names = recognizedStudents.joinToString(", ") { "${it.firstName} ${it.lastName}" }
                    Toast.makeText(requireContext(), "✅ Recognized and marked present: $names", Toast.LENGTH_LONG).show()
                    Log.d("AttendanceFragment", "Successfully recognized ${recognizedStudents.size} student(s)")
                } else {
                    Toast.makeText(requireContext(), "❌ No enrolled students recognized in the image. Make sure students are clearly visible.", Toast.LENGTH_LONG).show()
                    Log.d("AttendanceFragment", "No students were recognized")
                }
            } catch (e: Exception) {
                Log.e("AttendanceFragment", "Error during face recognition", e)
                Toast.makeText(requireContext(), "Error during face recognition: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                resetRecognitionUI()
            }
        }
    }

    private fun calculateCosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        if (embedding1.size != embedding2.size) return 0f
        var dot = 0f; var norm1 = 0f; var norm2 = 0f
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
                Log.w("AttendanceFragment", "Save operation timed out after 30 seconds")
                Toast.makeText(requireContext(), "Save operation timed out. Please check your connection and try again.", Toast.LENGTH_LONG).show()
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
                }
            )
        } catch (e: Exception) {
            Log.e("AttendanceFragment", "Navigation error: ${e.message}")
            Toast.makeText(requireContext(), "Navigation error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cancelSaveTimeout()
    }
}
