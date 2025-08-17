package com.example.raw_pesms.data.model

import com.example.raw_pesms.data.model.AttendanceStatus

data class Student(
    val id: String = "",
    val firstName: String = "",
    val middleInitial: String = "",
    val lastName: String = "",
    val guardianPhone: String? = null, // New field for guardian phone number
    val grade: String = "",
    val section: String = "",
    val faceEmbedding: String? = null,
    val faceImagePath: String? = null,
    var attendanceStatus: AttendanceStatus = AttendanceStatus.ABSENT,
    val ownerId: String = "",
    val faceEnrolled: Boolean = false,
    val createdAt: com.google.firebase.Timestamp? = null
) {
    // Secondary constructor for Firestore deserialization
    constructor() : this(
        id = "",
        firstName = "",
        middleInitial = "",
        lastName = "",
        guardianPhone = null,
        grade = "",
        section = "",
        faceEmbedding = null,
        faceImagePath = null,
        attendanceStatus = AttendanceStatus.ABSENT,
        ownerId = "",
        faceEnrolled = false,
        createdAt = null
    )

    // Helper method to get full name
    fun getFullName(): String {
        val middle = if (middleInitial.isNotBlank()) " $middleInitial." else ""
        return "$firstName$middle $lastName"
    }

    // Helper method to get formatted guardian phone
    fun getFormattedGuardianPhone(): String {
        return guardianPhone?.let { phone ->
            if (phone.length == 11 && phone.startsWith("09")) {
                // Format: 0912-345-6789
                "${phone.substring(0, 4)}-${phone.substring(4, 7)}-${phone.substring(7)}"
            } else {
                phone
            }
        } ?: "No phone number"
    }

    // Helper method to validate guardian phone
    fun hasValidGuardianPhone(): Boolean {
        return guardianPhone?.matches(Regex("^09[0-9]{9}$")) == true
    }
    fun fullName(): String = getFullName()
}
