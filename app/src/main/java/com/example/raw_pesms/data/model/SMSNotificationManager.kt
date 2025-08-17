package com.example.raw_pesms.data.model

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

class SMSNotificationManager(private val context: Context) {

    companion object {
        private const val TAG = "SMSNotificationManager"

        private val DATE_FORMAT = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
        private val TIME_FORMAT = SimpleDateFormat("h:mm a", Locale.getDefault())
    }

    fun sendAbsentNotification(student: Student, grade: String, section: String) {
        if (!checkSmsPermission()) {
            Log.w(TAG, "SMS permission not granted")
            return
        }

        val guardianPhone = student.guardianPhone
        if (guardianPhone.isNullOrBlank()) {
            Log.w(TAG, "No guardian phone number for ${student.firstName} ${student.lastName}")
            return
        }

        val message = createAbsentMessage(student, grade, section)

        try {
            sendSMS(guardianPhone, message)
            Log.d(TAG, "SMS sent successfully to $guardianPhone for ${student.firstName}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS to $guardianPhone", e)
        }
    }

    fun sendMultipleAbsentNotifications(absentStudents: List<Student>, grade: String, section: String) {
        if (!checkSmsPermission()) {
            Log.w(TAG, "SMS permission not granted for bulk notifications")
            return
        }

        var successCount = 0
        var failureCount = 0

        absentStudents.forEach { student ->
            try {
                sendAbsentNotification(student, grade, section)
                successCount++
            } catch (e: Exception) {
                failureCount++
                Log.e(TAG, "Failed to send SMS for ${student.firstName}", e)
            }
        }

        Log.i(TAG, "Bulk SMS finished: $successCount sent, $failureCount failed")
    }

    private fun createAbsentMessage(student: Student, grade: String, section: String): String {
        val currentDate = DATE_FORMAT.format(Date())
        val currentTime = TIME_FORMAT.format(Date())

        return """
            ATTENDANCE NOTIFICATION
            
            Dear Guardian,
            
            Your child ${student.firstName} ${student.lastName} was marked ABSENT from class today.
            
            Details:
            • Date: $currentDate
            • Time: $currentTime
            • Grade: $grade
            • Section: $section
            
            If this is unexpected, please contact the school immediately.
            
            - School Management System
        """.trimIndent()
    }

    fun createCustomAbsentMessage(
        student: Student,
        grade: String,
        section: String,
        schoolName: String = "PESMS School"
    ): String {
        val date = DATE_FORMAT.format(Date())
        val time = TIME_FORMAT.format(Date())

        return """
            📚 $schoolName - Attendance Alert
            
            Hello! Your child ${student.firstName} ${student.lastName} was marked ABSENT today ($date at $time) in Grade $grade - Section $section.
            
            If this is an error or your child is sick, please inform the class teacher.
            
            Thank you.
        """.trimIndent()
    }

    private fun sendSMS(phoneNumber: String, message: String) {
        val smsManager = SmsManager.getDefault()
        val cleanPhone = phoneNumber.replace(Regex("[^+\\d]"), "")

        if (message.length > 160) {
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(cleanPhone, null, parts, null, null)
            Log.d(TAG, "Multi-part SMS sent to $cleanPhone (${parts.size} parts)")
        } else {
            smsManager.sendTextMessage(cleanPhone, null, message, null, null)
            Log.d(TAG, "Single SMS sent to $cleanPhone")
        }
    }

    fun checkSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
