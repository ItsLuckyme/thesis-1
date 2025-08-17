package com.example.raw_pesms.UI.attendance

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.raw_pesms.data.model.AttendanceRecord
import com.example.raw_pesms.databinding.ItemAttendanceCardBinding
import java.text.SimpleDateFormat
import java.util.*

class AttendanceCardAdapter : ListAdapter<AttendanceRecord, AttendanceCardAdapter.AttendanceViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendanceViewHolder {
        val binding = ItemAttendanceCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AttendanceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AttendanceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AttendanceViewHolder(private val binding: ItemAttendanceCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(record: AttendanceRecord) {
            binding.tvStudentName.text = record.studentName
            binding.tvGradeSection.text = "${record.grade} - ${record.section}"
            binding.tvDate.text = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(record.date))
            binding.tvStatus.text = record.status

            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val formattedDate = formatter.format(Date(record.date))
            binding.tvDate.text = formattedDate
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AttendanceRecord>() {
        override fun areItemsTheSame(oldItem: AttendanceRecord, newItem: AttendanceRecord) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: AttendanceRecord, newItem: AttendanceRecord) = oldItem == newItem
    }
}
