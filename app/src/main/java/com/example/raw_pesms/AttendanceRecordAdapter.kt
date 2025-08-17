package com.example.raw_pesms.UI

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.raw_pesms.data.model.AttendanceRecord
import com.example.raw_pesms.databinding.ItemAttendanceRecordBinding
import java.text.SimpleDateFormat
import java.util.*

class AttendanceRecordAdapter : ListAdapter<AttendanceRecord, AttendanceRecordAdapter.AttendanceRecordViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendanceRecordViewHolder {
        val binding = ItemAttendanceRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AttendanceRecordViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AttendanceRecordViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    class AttendanceRecordViewHolder(private val binding: ItemAttendanceRecordBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AttendanceRecord) {
            binding.tvStudentName.text = item.studentName
            binding.tvGrade.text = item.grade ?: ""
            binding.tvSection.text = item.section ?: ""
            binding.tvStatus.text = item.status

            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val formattedDate = formatter.format(Date(item.date))
            binding.tvDate.text = formattedDate
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AttendanceRecord>() {
        override fun areItemsTheSame(oldItem: AttendanceRecord, newItem: AttendanceRecord): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AttendanceRecord, newItem: AttendanceRecord): Boolean {
            return oldItem == newItem
        }
    }
}
