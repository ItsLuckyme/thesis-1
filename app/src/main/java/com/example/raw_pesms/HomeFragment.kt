package com.example.raw_pesms

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

class HomeFragment : Fragment(R.layout.fragment_home) {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnAttendanceHistory = view.findViewById<Button>(R.id.btnAttendanceHistory)
        val btnCheckAttendance = view.findViewById<Button>(R.id.btnCheckAttendance)

        btnAttendanceHistory.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_attendanceHistoryFragment)
        }

        btnCheckAttendance.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_selectClassFragment)
        }
    }
}
