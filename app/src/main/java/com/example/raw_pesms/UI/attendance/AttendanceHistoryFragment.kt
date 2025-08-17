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

        Log.d(
            "AttendanceHistoryFragment",
            "onCreate: actionType=$actionType, grade=$preselectedGrade, section=$preselectedSection"
        )

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

        Log.d(
            "AttendanceHistoryFragment",
            "onViewCreated: actionType=$actionType, preselected: $preselectedGrade-$preselectedSection"
        )

        // Auto navigate when coming from attendance recording
        if (shouldAutoNavigate()) {
            Log.d("AttendanceHistoryFragment", "Setting up auto-navigation")

            // Observe grades first
            attendanceViewModel.grades.observe(viewLifecycleOwner) { grades ->
                if (grades.isNotEmpty() && preselectedGrade in grades) {
                    Log.d(
                        "AttendanceHistoryFragment",
                        "Grades loaded, loading sections for $preselectedGrade"
                    )
                    attendanceViewModel.loadSections(preselectedGrade!!)
                }
            }

            // Then observe sections and auto-navigate
            attendanceViewModel.sections.observe(viewLifecycleOwner) { sections ->
                if (sections.isNotEmpty() && preselectedSection in sections && !hasAutoNavigated) {
                    Log.d(
                        "AttendanceHistoryFragment",
                        "Auto-navigating to history with $preselectedGrade-$preselectedSection"
                    )
                    hasAutoNavigated = true
                    // Use a small delay to ensure UI is ready
                    view.postDelayed({
                        proceedToHistory()
                    }, 500)
                }
            }
        }
    }

    private fun setupSpinners() {
        Log.d("AttendanceHistoryFragment", "Setting up spinners")

        // Load grades when fragment starts
        attendanceViewModel.loadGrades()

        // Setup Grade Spinner
        attendanceViewModel.grades.observe(viewLifecycleOwner) { grades ->
            Log.d("AttendanceHistoryFragment", "Grades received: $grades")
            if (grades.isNotEmpty()) {
                val gradeAdapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    grades
                )
                gradeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                gradeSpinner.adapter = gradeAdapter

                // Set preselected grade if available
                preselectedGrade?.let { preselected ->
                    val position = grades.indexOf(preselected)
                    if (position >= 0) {
                        gradeSpinner.setSelection(position)
                        selectedGrade = preselected
                        // Load sections for preselected grade
                        attendanceViewModel.loadSections(preselected)
                        Log.d("AttendanceHistoryFragment", "Preselected grade: $preselected")
                    }
                }
            }
        }

        // Setup Grade Spinner Selection Listener
        gradeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedGrade = parent?.getItemAtPosition(position) as? String
                selectedGrade?.let { grade ->
                    Log.d("AttendanceHistoryFragment", "Grade selected: $grade")
                    // Load sections for selected grade
                    attendanceViewModel.loadSections(grade)
                    // Reset section selection
                    selectedSection = null
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedGrade = null
            }
        }

        // Setup Section Spinner
        attendanceViewModel.sections.observe(viewLifecycleOwner) { sections ->
            Log.d("AttendanceHistoryFragment", "Sections received: $sections")
            if (sections.isNotEmpty()) {
                val sectionAdapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    sections
                )
                sectionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                sectionSpinner.adapter = sectionAdapter

                // Set preselected section if available
                preselectedSection?.let { preselected ->
                    val position = sections.indexOf(preselected)
                    if (position >= 0) {
                        sectionSpinner.setSelection(position)
                        selectedSection = preselected
                        Log.d("AttendanceHistoryFragment", "Preselected section: $preselected")
                    }
                }
            } else {
                // Clear section spinner if no sections available
                sectionSpinner.adapter = null
                selectedSection = null
            }
        }

        // Setup Section Spinner Selection Listener
        sectionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedSection = parent?.getItemAtPosition(position) as? String
                Log.d("AttendanceHistoryFragment", "Section selected: $selectedSection")
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedSection = null
            }
        }
    }

    private fun setupButton() {
        Log.d("AttendanceHistoryFragment", "Setting up proceed button")
        proceedButton.setOnClickListener {
            proceedToHistory()
        }
    }

    private fun shouldAutoNavigate(): Boolean {
        val shouldAuto = !preselectedGrade.isNullOrBlank() &&
                !preselectedSection.isNullOrBlank() &&
                actionType == "history" &&
                !hasAutoNavigated

        Log.d(
            "AttendanceHistoryFragment",
            "shouldAutoNavigate: $shouldAuto (grade=$preselectedGrade, section=$preselectedSection, actionType=$actionType, hasAutoNavigated=$hasAutoNavigated)"
        )
        return shouldAuto
    }

    private fun proceedToHistory() {
        val gradeToUse = selectedGrade ?: preselectedGrade
        val sectionToUse = selectedSection ?: preselectedSection

        if (gradeToUse.isNullOrBlank() || sectionToUse.isNullOrBlank()) {
            Toast.makeText(
                requireContext(),
                "Please select both grade and section",
                Toast.LENGTH_SHORT
            ).show()
            Log.w(
                "AttendanceHistoryFragment",
                "Cannot proceed: grade=$gradeToUse, section=$sectionToUse"
            )
            return
        }

        Log.d(
            "AttendanceHistoryFragment",
            "Proceeding to history/attendance with grade=$gradeToUse, section=$sectionToUse, actionType=$actionType"
        )

        val bundle = Bundle().apply {
            putString("grade", gradeToUse)
            putString("section", sectionToUse)
        }

        try {
            if (actionType == "attendance") {
                findNavController().navigate(
                    R.id.action_attendanceHistoryFragment_to_attendanceFragment,
                    bundle
                )
            } else {
                // This should be the default path for viewing history
                findNavController().navigate(
                    R.id.action_attendanceHistoryFragment_to_historyListFragment,
                    bundle
                )
            }
        } catch (e: Exception) {
            Log.e("AttendanceHistoryFragment", "Navigation error", e)
            Toast.makeText(requireContext(), "Navigation error: ${e.message}", Toast.LENGTH_SHORT)
                .show()
        }
    }
}