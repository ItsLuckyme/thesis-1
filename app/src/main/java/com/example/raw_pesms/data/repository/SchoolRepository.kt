package com.example.raw_pesms.data.repository

import com.example.raw_pesms.data.model.AttendanceRecord
import com.example.raw_pesms.data.model.Student
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class SchoolRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    // ----------------- grade & section “folders” -----------------
    fun ensureGrade(ownerId: String, grade: String) =
        db.collection("owners").document(ownerId)
            .collection("grades").document(grade)
            .set(mapOf("name" to grade))

    fun ensureSection(ownerId: String, grade: String, section: String) =
        db.collection("owners").document(ownerId)
            .collection("grades").document(grade)
            .collection("sections").document(section)
            .set(mapOf("name" to section))

    fun grades(ownerId: String): Flow<List<String>> = callbackFlow {
        val ref = db.collection("owners").document(ownerId)
            .collection("grades").orderBy("name", Query.Direction.ASCENDING)
        val reg = ref.addSnapshotListener { snap, e ->
            if (e != null) { trySend(emptyList()); return@addSnapshotListener }
            val result = snap?.documents?.mapNotNull { it.id } ?: emptyList()
            trySend(result)
        }
        awaitClose { reg.remove() }
    }

    fun sections(ownerId: String, grade: String): Flow<List<String>> = callbackFlow {
        val ref = db.collection("owners").document(ownerId)
            .collection("grades").document(grade)
            .collection("sections").orderBy("name", Query.Direction.ASCENDING)
        val reg = ref.addSnapshotListener { snap, e ->
            if (e != null) { trySend(emptyList()); return@addSnapshotListener }
            val result = snap?.documents?.mapNotNull { it.id } ?: emptyList()
            trySend(result)
        }
        awaitClose { reg.remove() }
    }

    // ----------------- students -----------------
    fun studentsByClass(ownerId: String, grade: String, section: String): Flow<List<Student>> = callbackFlow {
        val ref = db.collection("owners").document(ownerId)
            .collection("grades").document(grade)
            .collection("sections").document(section)
            .collection("students")
            .orderBy("lastName", Query.Direction.ASCENDING)
        val reg = ref.addSnapshotListener { snapshot, e ->
            if (e != null) { trySend(emptyList()); return@addSnapshotListener }
            val list = snapshot?.documents?.map { doc ->
                doc.toObject(Student::class.java)?.copy(id = doc.id) ?: Student()
            } ?: emptyList()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    suspend fun addStudent(ownerId: String, student: Student): String {
        ensureGrade(ownerId, student.grade)
        ensureSection(ownerId, student.grade, student.section)
        val ref = db.collection("owners").document(ownerId)
            .collection("grades").document(student.grade)
            .collection("sections").document(student.section)
            .collection("students").add(student.copy(ownerId = ownerId))
            .await()
        return ref.id
    }

    suspend fun deleteStudent(ownerId: String, grade: String, section: String, studentId: String) {
        db.collection("owners").document(ownerId)
            .collection("grades").document(grade)
            .collection("sections").document(section)
            .collection("students").document(studentId)
            .delete()
    }

    suspend fun updateStudentFace(ownerId: String, studentId: String, grade: String, section: String,
                                  embedding: String?, photo: ByteArray?) {
        val updates = mutableMapOf<String, Any?>(
            "faceEmbedding" to embedding
        )
        if (photo != null) updates["facePhoto"] = photo
        db.collection("owners").document(ownerId)
            .collection("grades").document(grade)
            .collection("sections").document(section)
            .collection("students").document(studentId)
            .update(updates)
    }

    // ----------------- attendance -----------------
    private fun dayKey(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return String.format("%04d-%02d-%02d", y, m, d)
    }

    suspend fun saveAttendance(ownerId: String, records: List<AttendanceRecord>) {
        val batch = db.batch()
        val coll = db.collection("owners").document(ownerId).collection("attendanceRecords")
        records.forEach { r ->
            val key = "${r.studentId}_${dayKey(r.date)}" // unique per day per student
            val doc = coll.document(key)
            val toSave = r.copy(id = key, ownerId = ownerId)
            batch.set(doc, toSave)
        }
        batch.commit()
    }

    fun allAttendance(ownerId: String): Flow<List<AttendanceRecord>> = callbackFlow {
        val ref = db.collection("owners").document(ownerId)
            .collection("attendanceRecords")
            .orderBy("date", Query.Direction.DESCENDING)
        val reg = ref.addSnapshotListener { snap, e ->
            if (e != null) { trySend(emptyList()); return@addSnapshotListener }
            val list = snap?.documents?.mapNotNull { it.toObject(AttendanceRecord::class.java) } ?: emptyList()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    fun attendanceByClass(ownerId: String, grade: String, section: String): Flow<List<AttendanceRecord>> =
        callbackFlow {
            val ref = db.collection("owners").document(ownerId)
                .collection("attendanceRecords")
                .whereEqualTo("grade", grade)
                .whereEqualTo("section", section)
                .orderBy("date", Query.Direction.DESCENDING)
            val reg = ref.addSnapshotListener { snap, e ->
                if (e != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents?.mapNotNull { it.toObject(AttendanceRecord::class.java) } ?: emptyList()
                trySend(list)
            }
            awaitClose { reg.remove() }
        }
}
