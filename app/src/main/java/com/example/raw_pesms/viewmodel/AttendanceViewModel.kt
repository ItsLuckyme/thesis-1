package com.example.raw_pesms.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.raw_pesms.data.model.AttendanceRecord
import com.example.raw_pesms.data.model.AttendanceStatus
import com.example.raw_pesms.data.model.Student
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AttendanceViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // LiveData
    private val _attendance = MutableLiveData<List<AttendanceRecord>>()
    val attendance: LiveData<List<AttendanceRecord>> get() = _attendance

    private val _students = MutableLiveData<List<Student>>()
    val students: LiveData<List<Student>> get() = _students

    private val _grades = MutableLiveData<List<String>>()
    val grades: LiveData<List<String>> get() = _grades

    private val _sections = MutableLiveData<List<String>>()
    val sections: LiveData<List<String>> get() = _sections

    private val _saveStatus = MutableLiveData<Boolean?>()
    val saveStatus: LiveData<Boolean?> = _saveStatus

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // Delete Status
    sealed class DeleteStatus {
        object Idle : DeleteStatus()
        data class Success(val message: String) : DeleteStatus()
        data class Error(val error: String) : DeleteStatus()
    }

    private val _deleteOperationStatus = MutableLiveData<DeleteStatus>(DeleteStatus.Idle)
    val deleteOperationStatus: LiveData<DeleteStatus> get() = _deleteOperationStatus

    fun clearSaveStatus() { _saveStatus.value = null }
    fun clearErrorMessage() { _errorMessage.value = null }

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
                    try {
                        Student(
                            id = doc.id,
                            firstName = doc.getString("firstName") ?: "",
                            middleInitial = doc.getString("middleInitial") ?: "",
                            lastName = doc.getString("lastName") ?: "",
                            guardianPhone = doc.getString("guardianPhone"), // ✅ ensure guardian phone is loaded
                            grade = doc.getString("grade") ?: "",
                            section = doc.getString("section") ?: "",
                            faceEmbedding = doc.getString("faceEmbedding"),
                            faceImagePath = doc.getString("faceImagePath"),
                            attendanceStatus = AttendanceStatus.ABSENT, // default
                            ownerId = doc.getString("ownerId") ?: "",
                            faceEnrolled = doc.getBoolean("faceEnrolled") ?: false,
                            createdAt = doc.getTimestamp("createdAt")
                        )
                    } catch (e: Exception) {
                        Log.e("AttendanceVM", "Error mapping student: ${doc.id}", e)
                        null
                    }
                }
                _students.postValue(list)
            }
            .addOnFailureListener { e ->
                Log.e("AttendanceVM", "Error loading students: ${e.message}")
                _errorMessage.postValue("Failed to load students: ${e.message}")
            }
    }

    /** Mark attendance before saving */
    fun markAttendance(studentId: String, status: AttendanceStatus) {
        val updated = _students.value?.map {
            if (it.id == studentId) it.copy(attendanceStatus = status) else it
        }
        _students.postValue(updated)
    }

    /** Load grades & sections */
    fun loadGrades() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("owners").document(uid)
            .collection("grades")
            .get()
            .addOnSuccessListener { snap -> _grades.postValue(snap.documents.map { it.id }) }
            .addOnFailureListener { e ->
                Log.e("AttendanceVM", "Error loading grades: ${e.message}")
            }
    }

    fun loadSections(grade: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("owners").document(uid)
            .collection("grades").document(grade)
            .collection("sections")
            .get()
            .addOnSuccessListener { snap -> _sections.postValue(snap.documents.map { it.id }) }
            .addOnFailureListener { e ->
                Log.e("AttendanceVM", "Error loading sections: ${e.message}")
            }
    }

    fun clearSections() { _sections.value = emptyList() }

    /** Save attendance sequentially using coroutines (fixed version) */
    fun saveAttendanceForClass(grade: String, section: String) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Log.e("AttendanceVM", "User not authenticated")
            _errorMessage.postValue("User not authenticated")
            _saveStatus.postValue(false)
            return
        }

        val studentsList = _students.value
        if (studentsList.isNullOrEmpty()) {
            Log.e("AttendanceVM", "No students to save attendance for")
            _errorMessage.postValue("No students to save attendance for")
            _saveStatus.postValue(false)
            return
        }

        Log.d("AttendanceVM", "Starting to save attendance for ${studentsList.size} students")
        val timestamp = System.currentTimeMillis()
        val chunks = studentsList.chunked(200) // safe batch size

        viewModelScope.launch {
            try {
                Log.d("AttendanceVM", "Processing ${chunks.size} chunks")

                for ((index, chunk) in chunks.withIndex()) {
                    Log.d("AttendanceVM", "Processing chunk ${index + 1}/${chunks.size} with ${chunk.size} students")

                    val batch = db.batch()

                    chunk.forEach { student ->
                        val attRef = db.collection("owners").document(uid)
                            .collection("grades").document(grade)
                            .collection("sections").document(section)
                            .collection("attendance")
                            .document()

                        val record = AttendanceRecord(
                            id = attRef.id,
                            studentId = student.id,
                            studentName = student.getFullName(),
                            grade = grade,
                            section = section,
                            status = student.attendanceStatus?.name ?: AttendanceStatus.ABSENT.name,
                            date = timestamp,
                            ownerId = uid
                        )
                        batch.set(attRef, record)
                    }

                    try {
                        batch.commit().await()
                        Log.d("AttendanceVM", "Batch ${index + 1}/${chunks.size} saved successfully")
                    } catch (batchException: Exception) {
                        Log.e("AttendanceVM", "Error saving batch ${index + 1}: ${batchException.message}")
                        throw batchException
                    }
                }

                Log.d("AttendanceVM", "All batches saved successfully")
                _saveStatus.postValue(true)

                loadAttendanceForClass(grade, section)

            } catch (e: Exception) {
                Log.e("AttendanceVM", "Error saving attendance: ${e.message}", e)
                _errorMessage.postValue("Failed to save attendance: ${e.message}")
                _saveStatus.postValue(false)
            }
        }
    }

    /** Load attendance records for a class */
    fun loadAttendanceForClass(grade: String, section: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("owners").document(uid)
            .collection("grades").document(grade)
            .collection("sections").document(section)
            .collection("attendance")
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    Log.e("AttendanceVM", "Error loading attendance: ${e.message}")
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { it.toObject(AttendanceRecord::class.java) } ?: emptyList()
                _attendance.postValue(list)
            }
    }

    /** Enroll a student face embedding */
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
            .addOnFailureListener { e -> Log.e("Firestore", "Face enrollment failed", e) }
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
            .addOnFailureListener { e -> Log.e("Firestore", "Error deleting student", e) }
    }

    /** Delete grade + sections + students + attendance recursively */
    fun deleteGradeWithSections(grade: String) {
        val uid = auth.currentUser?.uid ?: return
        val gradeRef = db.collection("owners").document(uid).collection("grades").document(grade)

        gradeRef.collection("sections").get()
            .addOnSuccessListener { sectionSnap ->
                val deleteTasks = mutableListOf<Task<Void>>()

                for (sectionDoc in sectionSnap.documents) {
                    val sectionRef = sectionDoc.reference

                    val studentTask = sectionRef.collection("students").get()
                        .continueWithTask { snap ->
                            Tasks.whenAll(snap.result?.documents?.map { it.reference.delete() } ?: emptyList())
                        }
                    deleteTasks.add(studentTask)

                    val attendanceTask = sectionRef.collection("attendance").get()
                        .continueWithTask { snap ->
                            Tasks.whenAll(snap.result?.documents?.map { it.reference.delete() } ?: emptyList())
                        }
                    deleteTasks.add(attendanceTask)

                    deleteTasks.add(sectionRef.delete())
                }

                Tasks.whenAll(deleteTasks)
                    .addOnSuccessListener {
                        gradeRef.delete()
                            .addOnSuccessListener {
                                _deleteOperationStatus.postValue(
                                    DeleteStatus.Success("Grade \"$grade\" deleted successfully")
                                )
                                loadGrades()
                            }
                            .addOnFailureListener { e ->
                                _deleteOperationStatus.postValue(DeleteStatus.Error(e.message ?: "Failed to delete grade"))
                            }
                    }
                    .addOnFailureListener { e ->
                        _deleteOperationStatus.postValue(DeleteStatus.Error(e.message ?: "Failed while deleting sections"))
                    }
            }
            .addOnFailureListener { e ->
                _deleteOperationStatus.postValue(DeleteStatus.Error(e.message ?: "Failed to load sections"))
            }
    }
}
