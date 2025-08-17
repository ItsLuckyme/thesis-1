package com.example.raw_pesms.UI.attendance

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.raw_pesms.R
import com.example.raw_pesms.data.model.AttendanceRecord
import com.example.raw_pesms.viewmodel.AttendanceViewModel
import com.example.raw_pesms.viewmodel.AttendanceViewModelFactory

class HistoryListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var titleText: TextView
    private lateinit var adapter: AttendanceHistoryAdapter

    private var grade: String? = null
    private var section: String? = null

    private val viewModel: AttendanceViewModel by activityViewModels {
        AttendanceViewModelFactory(requireActivity().application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            grade = it.getString("grade")
            section = it.getString("section")
        }
        Log.d("HistoryListFragment", "onCreate: grade=$grade, section=$section")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_attendance_history_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.rvAttendanceHistory)
        titleText = view.findViewById(R.id.tvTitle)

        setupRecyclerView()
        setupTitle()

        if (grade != null && section != null) {
            loadAttendanceHistory()
        } else {
            Toast.makeText(requireContext(), "Missing grade or section information", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        adapter = AttendanceHistoryAdapter(emptyList()) // ✅ pass empty list first
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun setupTitle() {
        if (grade != null && section != null) {
            titleText.text = "Attendance History - Grade $grade Section $section"
        } else {
            titleText.text = "Attendance History"
        }
    }

    private fun loadAttendanceHistory() {
        Log.d("HistoryListFragment", "Loading attendance history for $grade $section")

        viewModel.loadAttendanceForClass(grade!!, section!!)

        viewModel.attendance.observe(viewLifecycleOwner) { attendanceList ->
            Log.d("HistoryListFragment", "Received ${attendanceList.size} attendance records")

            if (attendanceList.isEmpty()) {
                Toast.makeText(requireContext(), "No attendance records found for $grade $section", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Loaded ${attendanceList.size} attendance records", Toast.LENGTH_SHORT).show()
            }

            adapter.updateData(attendanceList) // ✅ update list when new data arrives
        }
    }

    override fun onResume() {
        super.onResume()
        if (grade != null && section != null) {
            Log.d("HistoryListFragment", "Refreshing attendance history on resume")
            viewModel.loadAttendanceForClass(grade!!, section!!)
        }
    }
}
