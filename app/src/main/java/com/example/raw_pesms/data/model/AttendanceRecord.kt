package com.example.raw_pesms.data.model

data class AttendanceRecord(
    val id: String = "",            // attendance docId = "{studentId}_{yyyy-MM-dd}"
    val studentId: String = "",
    val studentName: String = "",
    val grade: String = "",
    val section: String = "",
    val status: String = "",
    val date: Long = 0L,            // millis for sorting
    val ownerId: String = ""
)
