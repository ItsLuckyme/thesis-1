package com.example.raw_pesms.data.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.raw_pesms.data.model.Attendance
import com.example.raw_pesms.data.model.AttendanceRecord
import com.example.raw_pesms.data.model.Student
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.util.Calendar

class AttendanceRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val studentsCol get() = firestore.collection("students")
    private val attendanceCol get() = firestore.collection("attendance_records")

    /** Compute [start,end] of day for a millis (device timezone) */
    private fun dayBounds(millis: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis
        return start to end
    }

    // -------------------------
    // Students (Firestore)
    // -------------------------

    fun getAllStudents(ownerId: String): Flow<List<Student>> = callbackFlow {
        val reg = studentsCol
            .whereEqualTo("ownerId", ownerId)
            .orderBy("lastName", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e("Firestore", "getAllStudents error", err)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { it.toObject(Student::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun getStudentsByClass(grade: String, section: String, ownerId: String): Flow<List<Student>> = callbackFlow {
        val reg = studentsCol
            .whereEqualTo("ownerId", ownerId)
            .whereEqualTo("grade", grade)
            .whereEqualTo("section", section)
            .orderBy("lastName", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e("Firestore", "getStudentsByClass error", err)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { it.toObject(Student::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    /** One-shot fetch used by your ViewModel. */
    suspend fun getStudentsByGradeAndSection(grade: String, section: String, ownerId: String): List<Student> {
        val res = studentsCol
            .whereEqualTo("ownerId", ownerId)
            .whereEqualTo("grade", grade)
            .whereEqualTo("section", section)
            .orderBy("lastName", Query.Direction.ASCENDING)
            .get()
            .await()
        return res.documents.mapNotNull { it.toObject(Student::class.java) }
    }

    /** Delete students by "First [Middle] Last" names for owner. */
    suspend fun deleteStudentsByNames(names: List<String>, ownerId: String) {
        if (names.isEmpty()) return
        val snap = studentsCol
            .whereEqualTo("ownerId", ownerId)
            .get()
            .await()

        val toDelete = snap.documents.filter { doc ->
            val s = doc.toObject(Student::class.java) ?: return@filter false
            s.fullName() in names
        }

        if (toDelete.isEmpty()) return
        val batch = firestore.batch()
        toDelete.forEach { batch.delete(it.reference) }
        batch.commit().await()
    }

    suspend fun deleteStudent(student: Student) {
        val snap = studentsCol
            .whereEqualTo("ownerId", student.ownerId)
            .whereEqualTo("id", student.id)
            .limit(1)
            .get()
            .await()
        val doc = snap.documents.firstOrNull() ?: return
        doc.reference.delete().await()
    }

    /** Adds a student, generates a stable Int `id`, returns it. */
    suspend fun addStudentAndReturnId(student: Student): Long {
        val id = SecureRandom().nextInt(Int.MAX_VALUE)
        val data = student.copy(id = id.toString())
        // Use an auto doc id; we rely on stored fields for lookups.
        studentsCol.add(data).await()
        return id.toLong()
    }

    /** Save/update face data for a student by Int id. */
    suspend fun saveStudentFace(studentId: Int, embedding: FloatArray, photo: ByteArray?) {
        val embeddingString = embedding.joinToString(",")
        val snap = studentsCol.whereEqualTo("id", studentId).limit(1).get().await()
        val doc = snap.documents.firstOrNull() ?: return
        val updates = hashMapOf<String, Any?>(
            "faceEmbedding" to embeddingString,
            "facePhoto" to photo
        )
        doc.reference.update(updates).await()
    }

    // -------------------------
    // Attendance Records (Firestore)
    // -------------------------

    /** Save list; prevent duplicates for same student/day; then add docs; also pushes to Firestore (only). */
    suspend fun saveAttendanceToRoomAndFirebase(records: List<AttendanceRecord>) {
        if (records.isEmpty()) return

        // Pre-delete conflicting records for same student/day
        val batch = firestore.batch()
        for (record in records) {
            val (start, end) = dayBounds(record.date)
            val dupSnap = attendanceCol
                .whereEqualTo("ownerId", record.ownerId)
                .whereEqualTo("studentId", record.studentId)
                .whereGreaterThanOrEqualTo("date", start)
                .whereLessThanOrEqualTo("date", end)
                .get()
                .await()
            dupSnap.documents.forEach { batch.delete(it.reference) }
        }
        batch.commit().await()

        // Insert fresh set
        val addBatch = firestore.batch()
        records.forEach { rec ->
            val ref = attendanceCol.document()
            addBatch.set(ref, rec)
        }
        addBatch.commit().await()
    }

    /** Called by your legacy flow; we implement day-based delete using the provided yyyy-MM-dd. */
    suspend fun deleteAttendanceByStudentAndDate(studentId: Int, date: String, ownerId: String) {
        // Parse yyyy-MM-dd -> millis bounds
        val parts = date.split("-")
        if (parts.size != 3) return
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, parts[0].toInt())
            set(Calendar.MONTH, parts[1].toInt() - 1)
            set(Calendar.DAY_OF_MONTH, parts[2].toInt())
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val (start, end) = dayBounds(cal.timeInMillis)
        val snap = attendanceCol
            .whereEqualTo("ownerId", ownerId)
            .whereEqualTo("studentId", studentId)
            .whereGreaterThanOrEqualTo("date", start)
            .whereLessThanOrEqualTo("date", end)
            .get()
            .await()
        if (!snap.isEmpty) {
            val batch = firestore.batch()
            snap.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    /** Legacy insert list: map Attendance -> AttendanceRecord now (kept behavior). */
    suspend fun insertAttendanceList(attendanceList: List<Attendance>) {
        if (attendanceList.isEmpty()) return

        // Build AttendanceRecord list using "now" millis for each record
        val now = System.currentTimeMillis()

        // We need student info for grade/section/name fallbacks
        val ownerId = attendanceList.first().ownerId
        val studentSnap = studentsCol.whereEqualTo("ownerId", ownerId).get().await()
        val students = studentSnap.documents.mapNotNull { it.toObject(Student::class.java) }

        val records = attendanceList.map { a ->
            val student = students.find { it.id == a.studentId }
            val studentName = listOfNotNull(
                student?.firstName,
                student?.middleInitial?.takeIf { it.isNotBlank() },
                student?.lastName
            ).joinToString(" ").ifBlank { a.studentName.ifBlank { "Unknown" } }

            AttendanceRecord(
                id = 0.toString(),
                studentId = a.studentId,
                studentName = studentName,
                grade = student?.grade ?: "Unknown",
                section = student?.section ?: "Unknown",
                status = a.status,
                date = now,
                ownerId = a.ownerId
            )
        }

        // duplicate-prevention per day per student + insert
        saveAttendanceToRoomAndFirebase(records)
    }

    // -------------------------
    // Queries for UI (LiveData)
    // -------------------------

    fun getAllAttendance(ownerId: String): LiveData<List<AttendanceRecord>> {
        val live = MutableLiveData<List<AttendanceRecord>>(emptyList())
        attendanceCol
            .whereEqualTo("ownerId", ownerId)
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e("Firestore", "getAllAttendance error", err)
                    live.postValue(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { it.toObject(AttendanceRecord::class.java) } ?: emptyList()
                live.postValue(list)
            }
        return live
    }

    fun getAttendanceByGradeSection(grade: String, section: String, ownerId: String): LiveData<List<AttendanceRecord>> {
        val live = MutableLiveData<List<AttendanceRecord>>(emptyList())
        attendanceCol
            .whereEqualTo("ownerId", ownerId)
            .whereEqualTo("grade", grade)
            .whereEqualTo("section", section)
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e("Firestore", "getAttendanceByGradeSection error", err)
                    live.postValue(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { it.toObject(AttendanceRecord::class.java) } ?: emptyList()
                live.postValue(list)
            }
        return live
    }

    fun getDistinctGrades(ownerId: String): LiveData<List<String>> {
        val live = MutableLiveData<List<String>>(emptyList())
        attendanceCol
            .whereEqualTo("ownerId", ownerId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e("Firestore", "getDistinctGrades error", err)
                    live.postValue(emptyList())
                    return@addSnapshotListener
                }
                val grades = snap?.documents
                    ?.mapNotNull { it.getString("grade") }
                    ?.distinct()
                    ?.sorted()
                    ?: emptyList()
                live.postValue(grades)
            }
        return live
    }

    fun getDistinctSections(grade: String, ownerId: String): LiveData<List<String>> {
        val live = MutableLiveData<List<String>>(emptyList())
        attendanceCol
            .whereEqualTo("ownerId", ownerId)
            .whereEqualTo("grade", grade)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e("Firestore", "getDistinctSections error", err)
                    live.postValue(emptyList())
                    return@addSnapshotListener
                }
                val sections = snap?.documents
                    ?.mapNotNull { it.getString("section") }
                    ?.distinct()
                    ?.sorted()
                    ?: emptyList()
                live.postValue(sections)
            }
        return live
    }

    // -------------------------
    // Delete Grade + Sections
    // -------------------------

    /**
     * Delete a grade and all its related data:
     * - Students under that grade
     * - Attendance records for that grade
     * - The grade document itself (if stored in "owners/{uid}/grades/{grade}")
     */
    suspend fun deleteGradeWithSections(grade: String, ownerId: String, callback: (Boolean, String?) -> Unit) {
        try {
            // 1. Delete all students in this grade
            val studentSnap = studentsCol
                .whereEqualTo("ownerId", ownerId)
                .whereEqualTo("grade", grade)
                .get()
                .await()
            val batch1 = firestore.batch()
            studentSnap.documents.forEach { batch1.delete(it.reference) }
            batch1.commit().await()

            // 2. Delete all attendance records in this grade
            val attendanceSnap = attendanceCol
                .whereEqualTo("ownerId", ownerId)
                .whereEqualTo("grade", grade)
                .get()
                .await()
            val batch2 = firestore.batch()
            attendanceSnap.documents.forEach { batch2.delete(it.reference) }
            batch2.commit().await()

            // 3. Delete grade document from /owners/{uid}/grades/{grade}
            val gradeRef = firestore.collection("owners")
                .document(ownerId)
                .collection("grades")
                .document(grade)

            gradeRef.delete().await()

            callback(true, null)
        } catch (e: Exception) {
            Log.e("Firestore", "deleteGradeWithSections error", e)
            callback(false, e.message)
        }
    }
}
