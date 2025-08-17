package com.example.raw_pesms.UI.attendance

import android.graphics.Bitmap
import android.os.Bundle
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
    private var saveTimeoutRunnable: Runnable? = null

    private val viewModel: AttendanceViewModel by activityViewModels {
        AttendanceViewModelFactory(requireActivity().application)
    }

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
            Toast.makeText(requireContext(), "AI Face Recognition placeholder invoked", Toast.LENGTH_SHORT).show()
            // hook point for face recognition later
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            grade = it.getString("grade")
            section = it.getString("section")
        }
        Log.d("AttendanceFragment", "onCreate: grade=$grade, section=$section")
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
                if (grade != null && section != null) {
                    viewModel.deleteStudent(grade!!, section!!, student.id) {
                        Toast.makeText(requireContext(), "Deleted ${student.firstName}", Toast.LENGTH_SHORT).show()
                        // Reload students after deletion
                        viewModel.loadStudents(grade!!, section!!)
                    }
                }
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun setupObservers() {
        if (grade != null && section != null) {
            viewModel.loadStudents(grade!!, section!!)

            viewModel.students.observe(viewLifecycleOwner) { students ->
                Log.d("AttendanceFragment", "Students loaded: ${students.size}")
                adapter.submitList(students)
            }
        }

        // Observe save status instead of attendance data
        viewModel.saveStatus.observe(viewLifecycleOwner) { isSuccess ->
            if (isSuccess != null) {
                cancelSaveTimeout()

                if (isSuccess) {
                    Log.d("AttendanceFragment", "Attendance saved successfully")
                    Toast.makeText(requireContext(), "Attendance saved!", Toast.LENGTH_SHORT).show()
                    navigateToHistory()
                } else {
                    Log.e("AttendanceFragment", "Failed to save attendance")
                    Toast.makeText(requireContext(), "Failed to save attendance. Please try again.", Toast.LENGTH_LONG).show()
                    resetSaveButton()
                }

                // Clear the status to avoid repeated notifications
                viewModel.clearSaveStatus()
            }
        }

        // Also observe error messages
        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMsg ->
            if (!errorMsg.isNullOrEmpty()) {
                cancelSaveTimeout()
                Log.e("AttendanceFragment", "Error: $errorMsg")
                Toast.makeText(requireContext(), "Error: $errorMsg", Toast.LENGTH_LONG).show()
                resetSaveButton()
                viewModel.clearErrorMessage()
            }
        }
    }

    private fun setupClickListeners(view: View) {
        btnSave.setOnClickListener {
            if (grade == null || section == null) {
                Toast.makeText(requireContext(), "Grade/Section missing", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val studentsList = viewModel.students.value
            if (studentsList.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "No students to save attendance for", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Log.d("AttendanceFragment", "Saving attendance for $grade $section")

            // Disable button to prevent multiple clicks
            btnSave.isEnabled = false
            btnSave.text = "Saving..."

            // Set up timeout mechanism
            setupSaveTimeout(view)

            // Save attendance
            viewModel.saveAttendanceForClass(grade!!, section!!)
        }

        view.findViewById<View>(R.id.btnCamera)?.setOnClickListener {
            takePicture.launch(null)
        }
    }

    private fun setupSaveTimeout(view: View) {
        saveTimeoutRunnable = Runnable {
            if (!btnSave.isEnabled) {
                Log.w("AttendanceFragment", "Save operation timed out")
                Toast.makeText(requireContext(), "Save operation taking longer than expected. Please check your connection and try again.", Toast.LENGTH_LONG).show()
                resetSaveButton()
            }
        }
        view.postDelayed(saveTimeoutRunnable, 15000) // Increased to 15 seconds
    }

    private fun cancelSaveTimeout() {
        saveTimeoutRunnable?.let { runnable ->
            view?.removeCallbacks(runnable)
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
            findNavController().navigate(
                R.id.action_attendanceFragment_to_attendanceHistoryFragment,
                bundle
            )
        } catch (e: Exception) {
            Log.e("AttendanceFragment", "Navigation error", e)
            Toast.makeText(requireContext(), "Navigation error", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cancelSaveTimeout()
    }
}