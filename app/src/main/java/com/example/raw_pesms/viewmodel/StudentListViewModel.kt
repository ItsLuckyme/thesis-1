package com.example.raw_pesms.viewmodel

import androidx.lifecycle.ViewModel
import com.example.raw_pesms.data.repository.AttendanceRepository

class StudentListViewModel(private val repository: AttendanceRepository) : ViewModel() {

    suspend fun getStudentsByGradeAndSection(grade: String, section: String, ownerId: String) =
        repository.getStudentsByGradeAndSection(grade, section, ownerId)

    suspend fun deleteStudentsByNames(names: List<String>, ownerId: String) {
        repository.deleteStudentsByNames(names, ownerId)
    }
}
