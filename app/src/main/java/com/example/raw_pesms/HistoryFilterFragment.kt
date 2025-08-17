package com.example.raw_pesms

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.raw_pesms.viewmodel.AttendanceViewModel
import com.example.raw_pesms.viewmodel.AttendanceViewModelFactory
import com.example.raw_pesms.UI.attendance.AttendanceHistoryAdapter

class HistoryFilterFragment : Fragment() {

    private lateinit var etGrade: EditText
    private lateinit var etSection: EditText
    private lateinit var etDate: EditText
    private lateinit var proceedButton: Button

    private val vm: AttendanceViewModel by activityViewModels {
        AttendanceViewModelFactory(requireActivity().application)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        inflater.inflate(R.layout.fragment_attendance_filter, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        etGrade = view.findViewById(R.id.etGrade)
        etSection = view.findViewById(R.id.etSection)
        etDate = view.findViewById(R.id.etDate)
        proceedButton = view.findViewById(R.id.btnFilter)

        // Example: open date picker when clicking date field
        etDate.setOnClickListener {
            val datePicker = DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    etDate.setText("$year-${month + 1}-$day")
                },
                2025, 0, 1
            )
            datePicker.show()
        }

        proceedButton.setOnClickListener {
            val grade = etGrade.text.toString().trim()
            val section = etSection.text.toString().trim()
            val date = etDate.text.toString().trim()

            if (grade.isEmpty() || section.isEmpty() || date.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val b = Bundle().apply {
                putString("grade", grade)
                putString("section", section)
                putString("date", date)
            }
            findNavController().navigate(
                R.id.action_historyFilterFragment_to_historyListFragment,
                b
            )
        }
    }
}
