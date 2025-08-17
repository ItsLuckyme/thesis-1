package com.example.raw_pesms.UI.attendance

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.raw_pesms.R
import com.example.raw_pesms.UI.AttendanceAdapter
import com.example.raw_pesms.data.model.AttendanceStatus
import com.example.raw_pesms.viewmodel.AttendanceViewModel
import com.example.raw_pesms.viewmodel.AttendanceViewModelFactory

class AttendanceFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnSave: Button
    private lateinit var adapter: AttendanceAdapter

    private var grade: String? = null
    private var section: String? = null
    private var saveTimeoutHandler: Handler? = null
    private var saveTimeoutRunnable: Runnable? = null
    private var isSaving = false

    private val viewModel: AttendanceViewModel by activityViewModels {
        AttendanceViewModelFactory(requireActivity().application)
    }

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
            Toast.makeText(requireContext(), "AI Face Recognition placeholder invoked", Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            grade = it.getString("grade")
            section = it.getString("section")
        }
        Log.d("AttendanceFragment", "onCreate: grade=$grade, section=$section")

        // Initialize handler
        saveTimeoutHandler = Handler(Looper.getMainLooper())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.fragment_attendance, container, false)
        recyclerView = v.findViewById(R.id.recyclerViewAttendance)
        btnSave = v.findViewById(R.id.btnSaveAttendance)

        setupRecyclerView()
        setupObservers()
        setupClickListeners(v)

        return v
    }

    private fun setupRecyclerView() {
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
                            Toast.makeText(requireContext(), "Deleted ${student.firstName}", Toast.LENGTH_SHORT).show()
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
    }

    private fun setupClickListeners(view: View) {
        btnSave.setOnClickListener {
            if (isSaving) {
                Toast.makeText(requireContext(), "Save already in progress...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (grade == null || section == null) {
                Toast.makeText(requireContext(), "Grade/Section missing", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val studentsList = viewModel.students.value
            if (studentsList.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "No students to save attendance for", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Log.d("AttendanceFragment", "Starting save process for $grade $section with ${studentsList.size} students")

            isSaving = true
            btnSave.isEnabled = false
            btnSave.text = "Saving..."

            setupSaveTimeout()

            // Call ViewModel function; it handles batch saving internally
            grade?.let { g ->
                section?.let { s ->
                    viewModel.saveAttendanceForClass(g, s)
                }
            }
        }

        view.findViewById<View>(R.id.btnCamera)?.setOnClickListener {
            takePicture.launch(null)
        }
    }

    private fun setupSaveTimeout() {
        cancelSaveTimeout() // Clear any existing timeout

        saveTimeoutRunnable = Runnable {
            if (isSaving) {
                Log.w("AttendanceFragment", "Save operation timed out after 30 seconds")
                Toast.makeText(
                    requireContext(),
                    "Save operation timed out. Please check your connection and try again.",
                    Toast.LENGTH_LONG
                ).show()

                isSaving = false
                resetSaveButton()

                // Clear the ViewModel states to prevent stale data
                viewModel.clearSaveStatus()
                viewModel.clearErrorMessage()
            }
        }

        saveTimeoutHandler?.postDelayed(saveTimeoutRunnable!!, 30000) // 30 second timeout
    }

    private fun cancelSaveTimeout() {
        saveTimeoutRunnable?.let { runnable ->
            saveTimeoutHandler?.removeCallbacks(runnable)
            saveTimeoutRunnable = null
        }
    }

    private fun resetSaveButton() {
        btnSave.isEnabled = true
        btnSave.text = "Save Attendance"
    }

    private fun navigateToHistory() {
        resetSaveButton()
        val bundle = Bundle().apply {
            putString("grade", grade)
            putString("section", section)
            putString("actionType", "history")
        }

        try {
            findNavController().navigate(R.id.action_attendanceFragment_to_attendanceHistoryFragment, bundle)
        } catch (e: Exception) {
            Log.e("AttendanceFragment", "Navigation error", e)
            Toast.makeText(requireContext(), "Navigation error", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cancelSaveTimeout()
        saveTimeoutHandler = null
    }

    override fun onPause() {
        super.onPause()
        // Reset states when fragment is paused
        if (isSaving) {
            cancelSaveTimeout()
            isSaving = false
            resetSaveButton()
        }
    }
}