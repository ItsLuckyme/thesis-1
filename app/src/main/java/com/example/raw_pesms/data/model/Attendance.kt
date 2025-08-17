package com.example.raw_pesms.data.model

data class Attendance(
    val studentId: String = "",       // student docId
    val studentName: String = "",
    val date: String = "",            // yyyy-MM-dd (human readable key)
    val status: String = "",          // PRESENT/ABSENT/LATE
    val ownerId: String = "",
    val grade: String = "",
    val section: String = ""
)

enum class AttendanceStatus { PRESENT, ABSENT, LATE }
