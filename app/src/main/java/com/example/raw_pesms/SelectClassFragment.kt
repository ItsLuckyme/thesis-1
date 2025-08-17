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
    private lateinit var btnDelete: Button   // Added delete button

    private var selectedGrade: String? = null
    private var selectedSection: String? = null

    private val vm: AttendanceViewModel by activityViewModels { AttendanceViewModelFactory(requireActivity().application) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_select_class, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        gradeSpinner = view.findViewById(R.id.spinnerGrade)
        sectionSpinner = view.findViewById(R.id.spinnerSection)
        btnProceed = view.findViewById(R.id.btnProceed)
        btnViewStudents = view.findViewById(R.id.btnViewStudents)
        val btnDelete = view.findViewById<ImageButton>(R.id.btnDelete) // Make sure you add this button in XML

        // Load grades initially
        vm.loadGrades()

        // Observe grades
        vm.grades.observe(viewLifecycleOwner) { grades ->
            gradeSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, grades)
            if (grades.isNotEmpty() && selectedGrade == null) {
                selectedGrade = grades[0]
                vm.loadSections(selectedGrade!!)
            } else if (grades.isEmpty()) {
                selectedGrade = null
                selectedSection = null
                sectionSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, emptyList<String>())
            }
            // Ensure selectedGrade still exists
            selectedGrade?.let { currentGrade ->
                if (!grades.contains(currentGrade)) {
                    selectedGrade = grades.firstOrNull()
                    selectedGrade?.let { vm.loadSections(it) }
                        ?: vm.clearSections()
                }
            }
        }

        gradeSpinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                val grade = p.getItemAtPosition(pos).toString()
                if (selectedGrade != grade) {
                    selectedGrade = grade
                    vm.loadSections(grade)
                }
            }
            override fun onNothingSelected(p: AdapterView<*>) {
                selectedGrade = null
                vm.clearSections()
            }
        }

        // Observe sections
        vm.sections.observe(viewLifecycleOwner) { sections ->
            sectionSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, sections)
            if (sections.isNotEmpty()) {
                if (selectedSection == null || !sections.contains(selectedSection)) {
                    selectedSection = sections[0]
                    sectionSpinner.setSelection(0)
                }
            } else {
                selectedSection = null
            }
        }

        sectionSpinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                selectedSection = p.getItemAtPosition(pos).toString()
            }
            override fun onNothingSelected(p: AdapterView<*>) {
                selectedSection = null
            }
        }

        // Proceed button
        btnProceed.setOnClickListener {
            if (selectedGrade != null && selectedSection != null) {
                val b = Bundle().apply { putString("grade", selectedGrade); putString("section", selectedSection) }
                findNavController().navigate(R.id.action_selectClassFragment_to_attendanceFragment, b)
            } else {
                Toast.makeText(requireContext(), "Please select grade and section", Toast.LENGTH_SHORT).show()
            }
        }

        // View Students button
        btnViewStudents.setOnClickListener {
            if (selectedGrade != null && selectedSection != null) {
                val b = Bundle().apply { putString("grade", selectedGrade); putString("section", selectedSection) }
                findNavController().navigate(R.id.action_selectClassFragment_to_studentListFragment, b)
            } else {
                Toast.makeText(requireContext(), "Please select grade and section", Toast.LENGTH_SHORT).show()
            }
        }

        // Delete button logic
        btnDelete.setOnClickListener {
            if (selectedGrade != null) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete Grade")
                    .setMessage("Are you sure you want to delete grade \"$selectedGrade\"?")
                    .setPositiveButton("Delete") { _, _ ->
                        vm.deleteGradeWithSections(selectedGrade!!)

                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                Toast.makeText(requireContext(), "No grade selected to delete", Toast.LENGTH_SHORT).show()
            }
        }

        // Observe delete operation result
        vm.deleteOperationStatus.observe(viewLifecycleOwner) { status ->
            when (status) {
                is AttendanceViewModel.DeleteStatus.Success -> {
                    Toast.makeText(requireContext(), status.message, Toast.LENGTH_SHORT).show()
                    vm.loadGrades()
                }
                is AttendanceViewModel.DeleteStatus.Error -> {
                    Toast.makeText(requireContext(), "Error: ${status.error}", Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
    }
}
