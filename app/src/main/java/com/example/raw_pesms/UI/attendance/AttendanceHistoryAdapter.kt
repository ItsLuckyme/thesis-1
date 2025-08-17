package com.example.raw_pesms.UI.attendance

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.raw_pesms.R
import com.example.raw_pesms.data.model.AttendanceRecord
import java.text.SimpleDateFormat
import java.util.*

class AttendanceHistoryAdapter(
    private var records: List<AttendanceRecord>
) : RecyclerView.Adapter<AttendanceHistoryAdapter.HistoryViewHolder>() {

    class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val studentName: TextView = itemView.findViewById(R.id.textStudentName)
        val status: TextView = itemView.findViewById(R.id.textStatus)
        val date: TextView = itemView.findViewById(R.id.textDate)
        val gradeSection: TextView = itemView.findViewById(R.id.textGradeSection)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendance_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val record = records[position]

        holder.studentName.text = record.studentName
        holder.status.text = record.status

        // Format date
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        holder.date.text = dateFormat.format(Date(record.date))

        holder.gradeSection.text = "${record.grade} - ${record.section}"
    }

    override fun getItemCount(): Int = records.size

    fun updateData(newRecords: List<AttendanceRecord>) {
        records = newRecords
        notifyDataSetChanged()
    }
}
