package com.example.raw_pesms.UI.attendance

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.raw_pesms.R
import com.example.raw_pesms.viewmodel.AttendanceViewModel
import com.example.raw_pesms.viewmodel.AttendanceViewModelFactory

class AttendanceHistoryFragment : Fragment() {

    private lateinit var proceedButton: Button
    private lateinit var gradeSpinner: Spinner
    private lateinit var sectionSpinner: Spinner

    private val attendanceViewModel: AttendanceViewModel by activityViewModels {
        AttendanceViewModelFactory(requireActivity().application)
    }

    private var selectedGrade: String? = null
    private var selectedSection: String? = null
    private var actionType: String? = null

    private var preselectedGrade: String? = null
    private var preselectedSection: String? = null
    private var hasAutoNavigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionType = arguments?.getString("actionType")
        preselectedGrade = arguments?.getString("grade")
        preselectedSection = arguments?.getString("section")

        Log.d("AttendanceHistoryFragment", "onCreate: actionType=$actionType, grade=$preselectedGrade, section=$preselectedSection")

        // Immediately assign preselected values
        selectedGrade = preselectedGrade
        selectedSection = preselectedSection
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_attendance_history, container, false)

        gradeSpinner = view.findViewById(R.id.gradeSpinner)
        sectionSpinner = view.findViewById(R.id.sectionSpinner)
        proceedButton = view.findViewById(R.id.buttonProceed)

        setupSpinners()
        setupButton()

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Auto navigate when coming from attendance recording
        if (shouldAutoNavigate()) {
            // Wait for sections to be populated, then auto-navigate
            attendanceViewModel.sections.observe(viewLifecycleOwner) { sections ->
                if (sections.isNotEmpty() && !hasAutoNavigated) {
                    Log.d("AttendanceHistoryFragment", "Auto-navigating to history")
                    hasAutoNavigated = true
                    view.post {
                        proceedToHistory()
                    }
                }
            }
        }
    }

    private fun shouldAutoNavigate(): Boolean {
        return !preselectedGrade.isNullOrBlank() &&
                !preselectedSection.isNullOrBlank() &&
                actionType == "history" &&
                !hasAutoNavigated
    }

    private fun setupSpinners() {
        attendanceViewModel.loadGrades()

        attendanceViewModel.grades.observe(viewLifecycleOwner) { grades ->
            val gradeList = listOf("Select Grade") + grades
            val gradeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, gradeList)
            gradeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            gradeSpinner.adapter = gradeAdapter

            // Set preselected grade
            preselectedGrade?.let { grade ->
                val position = gradeList.indexOf(grade)
                if (position != -1) {
                    gradeSpinner.setSelection(position)
                    attendanceViewModel.loadSections(grade)
                }
            }
        }

        gradeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) {
                val grade = parent.getItemAtPosition(pos).toString()
                if (grade != "Select Grade") {
                    selectedGrade = grade
                    attendanceViewModel.loadSections(grade)
                } else {
                    selectedGrade = null
                    attendanceViewModel.clearSections()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        attendanceViewModel.sections.observe(viewLifecycleOwner) { sections ->
            val sectionList = listOf("Select Section") + sections
            val sectionAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, sectionList)
            sectionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            sectionSpinner.adapter = sectionAdapter

            // Set preselected section
            preselectedSection?.let { section ->
                val pos = sectionList.indexOf(section)
                if (pos != -1) {
                    sectionSpinner.setSelection(pos)
                }
            }
        }

        sectionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) {
                val section = parent.getItemAtPosition(pos).toString()
                selectedSection = if (section != "Select Section") section else null
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupButton() {
        proceedButton.setOnClickListener {
            proceedToHistory()
        }
    }

    private fun proceedToHistory() {
        if (selectedGrade.isNullOrBlank() || selectedSection.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Please select both grade and section", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("AttendanceHistoryFragment", "Proceeding to history/attendance with grade=$selectedGrade, section=$selectedSection")

        val bundle = Bundle().apply {
            putString("grade", selectedGrade)
            putString("section", selectedSection)
        }

        try {
            if (actionType == "attendance") {
                findNavController().navigate(
                    R.id.action_attendanceHistoryFragment_to_attendanceFragment,
                    bundle
                )
            } else {
                findNavController().navigate(
                    R.id.action_attendanceHistoryFragment_to_historyListFragment,
                    bundle
                )
            }
        } catch (e: Exception) {
            Log.e("AttendanceHistoryFragment", "Navigation error", e)
            Toast.makeText(requireContext(), "Navigation error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}