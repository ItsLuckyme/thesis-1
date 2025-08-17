package com.example.raw_pesms.UI

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.raw_pesms.R
import com.example.raw_pesms.data.model.Student
import com.google.android.material.button.MaterialButton

class AttendanceAdapter(
    private val onStatusChanged: (Student, String) -> Unit,
    private val onDeleteClicked: (Student) -> Unit
) : ListAdapter<Student, AttendanceAdapter.AttendanceViewHolder>(StudentDiffCallback()) {

    private val attendanceMap = mutableMapOf<String, String>() // studentId -> status

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendanceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_attendance_student, parent, false)
        return AttendanceViewHolder(view)
    }

    override fun onBindViewHolder(holder: AttendanceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AttendanceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textStudentName: TextView = itemView.findViewById(R.id.textStudentName)
        private val textGradeSection: TextView = itemView.findViewById(R.id.textGradeSection)
        private val radioGroup: RadioGroup = itemView.findViewById(R.id.radioGroupStatus)
        private val btnDelete: MaterialButton = itemView.findViewById(R.id.btnDeleteAttendance)

        fun bind(student: Student) {
            textStudentName.text = student.fullName()
            textGradeSection.text = "${student.grade} - ${student.section}"

            btnDelete.setOnClickListener { onDeleteClicked(student) }

            radioGroup.setOnCheckedChangeListener(null)
            when (attendanceMap[student.id] ?: "Present") {
                "Present" -> radioGroup.check(R.id.radioPresent)
                "Absent" -> radioGroup.check(R.id.radioAbsent)
                "Late" -> radioGroup.check(R.id.radioLate)
            }
            radioGroup.setOnCheckedChangeListener { _, checkedId ->
                val status = when (checkedId) {
                    R.id.radioPresent -> "Present"
                    R.id.radioAbsent -> "Absent"
                    R.id.radioLate -> "Late"
                    else -> "Present"
                }
                attendanceMap[student.id] = status
                onStatusChanged(student, status)
            }
        }
    }

    fun getMarkedAttendance(): Map<String, String> = attendanceMap

    class StudentDiffCallback : DiffUtil.ItemCallback<Student>() {
        override fun areItemsTheSame(oldItem: Student, newItem: Student) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Student, newItem: Student) = oldItem == newItem
    }
}
