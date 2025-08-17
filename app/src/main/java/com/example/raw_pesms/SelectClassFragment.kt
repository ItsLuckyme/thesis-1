package com.example.raw_pesms

import android.app.AlertDialog
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.raw_pesms.viewmodel.AttendanceViewModel
import com.example.raw_pesms.viewmodel.AttendanceViewModelFactory

class SelectClassFragment : Fragment() {

    private lateinit var gradeSpinner: Spinner
    private lateinit var sectionSpinner: Spinner
    private lateinit var btnProceed: Button
    private lateinit var btnViewStudents: Button
    private lateinit var btnDelete: ImageButton

    private var selectedGrade: String? = null
    private var selectedSection: String? = null

    private val vm: AttendanceViewModel by activityViewModels {
        AttendanceViewModelFactory(requireActivity().application)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_select_class, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        gradeSpinner = view.findViewById(R.id.spinnerGrade)
        sectionSpinner = view.findViewById(R.id.spinnerSection)
        btnProceed = view.findViewById(R.id.btnProceed)
        btnViewStudents = view.findViewById(R.id.btnViewStudents)
        btnDelete = view.findViewById(R.id.btnDelete)

        setupSpinners()
        setupButtons()
        observeViewModel()

        testAttendanceFragmentInstantiation()

        vm.loadGrades()
    }

    private fun setupSpinners() {
        gradeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val grade = parent?.getItemAtPosition(position).toString()
                if (selectedGrade != grade) {
                    selectedGrade = grade
                    selectedSection = null
                    vm.loadSections(grade)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedGrade = null
                selectedSection = null
                vm.clearSections()
            }
        }

        sectionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedSection = parent?.getItemAtPosition(position).toString()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedSection = null
            }
        }
    }

    private fun setupButtons() {
        btnProceed.setOnClickListener {
            if (validateSelection()) {
                val bundle = Bundle().apply {
                    putString("grade", selectedGrade)
                    putString("section", selectedSection)
                }
                try {
                    findNavController().navigate(R.id.action_selectClassFragment_to_attendanceFragment, bundle)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Navigation failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnViewStudents.setOnClickListener {
            if (validateSelection()) {
                val bundle = Bundle().apply {
                    putString("grade", selectedGrade)
                    putString("section", selectedSection)
                }
                try {
                    findNavController().navigate(R.id.action_selectClassFragment_to_studentListFragment, bundle)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Navigation error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnDelete.setOnClickListener {
            selectedGrade?.let { grade ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete Grade")
                    .setMessage("Are you sure you want to delete grade \"$grade\" and all its sections?")
                    .setPositiveButton("Delete") { _, _ -> vm.deleteGradeWithSections(grade) }
                    .setNegativeButton("Cancel", null)
                    .show()
            } ?: Toast.makeText(requireContext(), "No grade selected to delete", Toast.LENGTH_SHORT).show()
        }
    }

    private fun testAttendanceFragmentInstantiation() {
        try {
            val testFragment = com.example.raw_pesms.UI.attendance.AttendanceFragment.newInstance("TestGrade", "TestSection")
            android.util.Log.d("SelectClassFragment", "✅ AttendanceFragment instantiated successfully")
        } catch (e: Exception) {
            android.util.Log.e("SelectClassFragment", "❌ AttendanceFragment instantiation failed", e)
            Toast.makeText(requireContext(), "Warning: AttendanceFragment may not be instantiated properly", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        vm.grades.observe(viewLifecycleOwner) { grades ->
            if (grades.isNotEmpty()) {
                gradeSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, grades)

                when {
                    selectedGrade == null -> {
                        selectedGrade = grades[0]
                        gradeSpinner.setSelection(0)
                        vm.loadSections(selectedGrade!!)
                    }
                    !grades.contains(selectedGrade) -> {
                        selectedGrade = grades.firstOrNull()
                        selectedSection = null
                        selectedGrade?.let {
                            gradeSpinner.setSelection(grades.indexOf(it))
                            vm.loadSections(it)
                        } ?: vm.clearSections()
                    }
                    else -> gradeSpinner.setSelection(grades.indexOf(selectedGrade))
                }
            } else {
                selectedGrade = null
                selectedSection = null
                gradeSpinner.adapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_dropdown_item, emptyList())

                sectionSpinner.adapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_dropdown_item, emptyList())

            }
        }

        vm.sections.observe(viewLifecycleOwner) { sections ->
            if (sections.isNotEmpty()) {
                sectionSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, sections)
                if (selectedSection == null || !sections.contains(selectedSection)) {
                    selectedSection = sections[0]
                    sectionSpinner.setSelection(0)
                } else {
                    sectionSpinner.setSelection(sections.indexOf(selectedSection))
                }
            } else {
                selectedSection = null
                sectionSpinner.adapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_dropdown_item, emptyList())

            }
        }

        vm.deleteOperationStatus.observe(viewLifecycleOwner) { status ->
            when (status) {
                is AttendanceViewModel.DeleteStatus.Success -> {
                    Toast.makeText(requireContext(), status.message, Toast.LENGTH_SHORT).show()
                    selectedGrade = null
                    selectedSection = null
                    vm.loadGrades()
                }
                is AttendanceViewModel.DeleteStatus.Error -> {
                    Toast.makeText(requireContext(), "Error: ${status.error}", Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
    }

    private fun validateSelection(): Boolean {
        return if (selectedGrade.isNullOrBlank() || selectedSection.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Please select both grade and section", Toast.LENGTH_SHORT).show()
            false
        } else {
            true
        }
    }
}
