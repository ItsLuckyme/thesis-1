package com.example.raw_pesms.data.model

data class Student(
    val id: String = "",                 // Firestore docId (string)
    val firstName: String = "",
    val middleInitial: String? = null,
    val lastName: String = "",
    val grade: String = "",
    val section: String = "",
    val ownerId: String = "",
    val faceEmbedding: String? = null,   // keep as comma-separated or base64 if you like
    val facePhoto: ByteArray? = null,
    val attendanceStatus: AttendanceStatus = AttendanceStatus.ABSENT // optional (store in Storage if large)
) {
    fun fullName(): String = if (middleInitial.isNullOrBlank()) {
        "$firstName $lastName"
    } else {
        "$firstName $middleInitial $lastName"
    }
}
