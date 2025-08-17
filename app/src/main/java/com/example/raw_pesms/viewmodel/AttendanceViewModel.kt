package com.example.raw_pesms.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.raw_pesms.data.model.AttendanceRecord
import com.example.raw_pesms.data.model.AttendanceStatus
import com.example.raw_pesms.data.model.Student
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks

class AttendanceViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Attendance LiveData
    private val _attendance = MutableLiveData<List<AttendanceRecord>>()
    val attendance: LiveData<List<AttendanceRecord>> get() = _attendance

    // Students LiveData
    private val _students = MutableLiveData<List<Student>>()
    val students: LiveData<List<Student>> get() = _students

    // Grades & Sections LiveData
    private val _grades = MutableLiveData<List<String>>()
    val grades: LiveData<List<String>> get() = _grades

    private val _sections = MutableLiveData<List<String>>()
    val sections: LiveData<List<String>> get() = _sections

    // Save Status LiveData (NEW)
    private val _saveStatus = MutableLiveData<Boolean?>()
    val saveStatus: LiveData<Boolean?> = _saveStatus

    // Error Message LiveData (NEW)
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // --- Delete Status
    sealed class DeleteStatus {
        object Idle : DeleteStatus()
        data class Success(val message: String) : DeleteStatus()
        data class Error(val error: String) : DeleteStatus()
    }

    private val _deleteOperationStatus = MutableLiveData<DeleteStatus>(DeleteStatus.Idle)
    val deleteOperationStatus: LiveData<DeleteStatus> get() = _deleteOperationStatus

    // Clear methods for new LiveData (NEW)
    fun clearSaveStatus() {
        _saveStatus.value = null
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    /** Load students for a grade & section */
    fun loadStudents(grade: String, section: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("owners").document(uid)
            .collection("grades").document(grade)
            .collection("sections").document(section)
            .collection("students")
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.mapNotNull { doc ->
                    doc.toObject(Student::class.java)?.copy(id = doc.id)
                }
                _students.postValue(list)
            }
    }

    /** Save attendance for all loaded students (improved with better status tracking) */
    fun saveAttendanceForClass(grade: String, section: String) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            _errorMessage.postValue("User not authenticated")
            _saveStatus.postValue(false)
            return
        }

        val studentsList = _students.value
        if (studentsList == null) {
            _errorMessage.postValue("No students found to save attendance for")
            _saveStatus.postValue(false)
            return
        }

        if (studentsList.isEmpty()) {
            _errorMessage.postValue("Student list is empty")
            _saveStatus.postValue(false)
            return
        }

        val timestamp = System.currentTimeMillis()

        // 🔹 Firestore batch limit = 500 → chunk into smaller groups
        val chunks = studentsList.chunked(450)

        var successCount = 0
        var hasFailure = false

        chunks.forEach { chunk ->
            val batch = db.batch()

            chunk.forEach { student ->
                val attRef = db.collection("owners").document(uid)
                    .collection("grades").document(grade)
                    .collection("sections").document(section)
                    .collection("attendance")
                    .document() // auto-id

                val record = AttendanceRecord(
                    id = attRef.id,
                    studentId = student.id,
                    studentName = student.fullName(),
                    grade = grade,
                    section = section,
                    status = student.attendanceStatus.name,
                    date = timestamp,
                    ownerId = uid
                )
                batch.set(attRef, record)
            }

            batch.commit()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        successCount++
                        android.util.Log.d("Attendance", "Batch $successCount/${chunks.size} saved")

                        // Check if ALL chunks are complete
                        if (successCount == chunks.size && !hasFailure) {
                            // All batches succeeded
                            _saveStatus.postValue(true)
                            loadAttendanceForClass(grade, section)
                        }
                    } else {
                        hasFailure = true
                        val errorMsg = task.exception?.message ?: "Unknown error occurred"
                        android.util.Log.e("Attendance", "Error saving attendance batch", task.exception)

                        // Only post failure status once
                        if (_saveStatus.value == null) {
                            _errorMessage.postValue("Failed to save attendance: $errorMsg")
                            _saveStatus.postValue(false)
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    hasFailure = true
                    val errorMsg = exception.message ?: "Network error occurred"
                    android.util.Log.e("Attendance", "Network error saving attendance", exception)

                    // Only post failure status once
                    if (_saveStatus.value == null) {
                        _errorMessage.postValue("Network error: $errorMsg")
                        _saveStatus.postValue(false)
                    }
                }
        }
    }

    /** Load attendance records for a specific class */
    fun loadAttendanceForClass(grade: String, section: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("owners").document(uid)
            .collection("grades").document(grade)
            .collection("sections").document(section)
            .collection("attendance")
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    android.util.Log.e("Attendance", "Snapshot listener error", e)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { it.toObject(AttendanceRecord::class.java) }
                    ?: emptyList()
                _attendance.postValue(list)
            }
    }

    /** Load all attendance across all grades/sections */
    fun loadAllAttendance() {
        val uid = auth.currentUser?.uid ?: return
        db.collectionGroup("attendance")
            .whereEqualTo("ownerId", uid)
            .orderBy("date", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.mapNotNull { it.toObject(AttendanceRecord::class.java) }
                _attendance.postValue(list)
            }
    }

    /** Mark individual student attendance before saving */
    fun markAttendance(studentId: String, status: AttendanceStatus) {
        val updated = _students.value?.map {
            if (it.id == studentId) it.copy(attendanceStatus = status) else it
        }
        _students.postValue(updated)
    }

    /** Load all available grades for the current owner */
    fun loadGrades() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("owners").document(uid)
            .collection("grades")
            .get()
            .addOnSuccessListener { snap ->
                _grades.postValue(snap.documents.map { it.id })
            }
    }

    /** Load all sections for a specific grade */
    fun loadSections(grade: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("owners").document(uid)
            .collection("grades").document(grade)
            .collection("sections")
            .get()
            .addOnSuccessListener { snap ->
                _sections.postValue(snap.documents.map { it.id })
            }
    }

    fun clearSections() {
        _sections.value = emptyList()
    }

    /** Enroll a student face embedding in Firestore */
    fun enrollStudentFace(
        studentId: String,
        grade: String,
        section: String,
        embedding: String?,
        extraData: Map<String, Any>? = null
    ) {
        val uid = auth.currentUser?.uid ?: return
        val docRef = db.collection("owners").document(uid)
            .collection("grades").document(grade)
            .collection("sections").document(section)
            .collection("students").document(studentId)

        val data = mutableMapOf<String, Any>()
        embedding?.let { data["faceEmbedding"] = it }
        extraData?.let { data.putAll(it) }

        docRef.update(data)
            .addOnFailureListener { e ->
                android.util.Log.e("Firestore", "Face enrollment failed", e)
            }
    }

    /** Delete a single student */
    fun deleteStudent(
        grade: String,
        section: String,
        studentId: String,
        onComplete: (() -> Unit)? = null
    ) {
        val uid = auth.currentUser?.uid ?: return
        val docRef = db.collection("owners").document(uid)
            .collection("grades").document(grade)
            .collection("sections").document(section)
            .collection("students").document(studentId)

        docRef.delete()
            .addOnSuccessListener { onComplete?.invoke() }
            .addOnFailureListener { e ->
                android.util.Log.e("Firestore", "Error deleting student", e)
            }
    }

    /** Recursively delete grade + sections + students + attendance */
    fun deleteGradeWithSections(grade: String) {
        val uid = auth.currentUser?.uid ?: return
        val gradeRef = db.collection("owners").document(uid).collection("grades").document(grade)

        gradeRef.collection("sections").get()
            .addOnSuccessListener { sectionSnap ->
                val deleteTasks = mutableListOf<Task<Void>>()

                for (sectionDoc in sectionSnap.documents) {
                    val sectionRef = sectionDoc.reference

                    // Delete all students
                    val studentTask = sectionRef.collection("students").get()
                        .continueWithTask { studentSnap ->
                            val innerTasks =
                                studentSnap.result?.documents?.map { it.reference.delete() }
                                    ?: emptyList()
                            Tasks.whenAll(innerTasks)
                        }
                    deleteTasks.add(studentTask)

                    // Delete all attendance
                    val attendanceTask = sectionRef.collection("attendance").get()
                        .continueWithTask { attSnap ->
                            val innerTasks =
                                attSnap.result?.documents?.map { it.reference.delete() }
                                    ?: emptyList()
                            Tasks.whenAll(innerTasks)
                        }
                    deleteTasks.add(attendanceTask)

                    // Delete section itself
                    deleteTasks.add(sectionRef.delete())
                }

                // After all sections cleaned up → delete grade
                Tasks.whenAll(deleteTasks)
                    .addOnSuccessListener {
                        gradeRef.delete()
                            .addOnSuccessListener {
                                _deleteOperationStatus.postValue(
                                    DeleteStatus.Success("Grade \"$grade\" deleted successfully")
                                )
                                loadGrades() // refresh UI
                            }
                            .addOnFailureListener { e ->
                                _deleteOperationStatus.postValue(
                                    DeleteStatus.Error(e.message ?: "Failed to delete grade")
                                )
                            }
                    }
                    .addOnFailureListener { e ->
                        _deleteOperationStatus.postValue(
                            DeleteStatus.Error(e.message ?: "Failed while deleting sections")
                        )
                    }
            }
            .addOnFailureListener { e ->
                _deleteOperationStatus.postValue(
                    DeleteStatus.Error(e.message ?: "Failed to load sections")
                )
            }
    }
}