package com.example.ble_app.data

enum class UserRole {
    TEACHER, STUDENT
}

data class User(
    val userId: Int,
    val email: String,
    val role: String,
    // Students must enroll their face before they can mark attendance.
    val faceEnrolled: Boolean = false
)

data class Classroom(
    val classId: Int,
    val name: String,
    val code: String,
    val teacherId: Int,
    val memberCount: Int? = 0,
    val createdAt: String? = null
)

data class JoinClassRequest(
    val code: String
)

data class CreateClassRequest(
    val name: String
)

data class AttendanceSession(
    val sessionId: Int,
    val classId: Int,
    val date: String,
    val startTime: String,
    val endTime: String,
    val zoneId: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    // HMAC key for the rotating BLE beacon code; only sent to the classroom's teacher.
    val beaconSecret: String
)

data class AttendanceRecord(
    val studentId: Int,
    val email: String,
    // PRESENT, ABSENT, LATE or PENDING_REVIEW (borderline face match awaiting the teacher)
    val status: String,
    val hasVerification: Boolean
)

data class AttendanceChallenge(
    val nonce: String,
    val actions: List<String>,
    val expiresAtEpochMillis: Long
)

data class FaceVerification(
    val referencePhoto: String?,
    val attendancePhoto: String,
    val similarity: Float,
    val spoofScore: Float,
    val reviewReason: String?
)

data class UpdateAttendanceRequest(
    val status: String
)

data class StudentAttendanceHistoryRecord(
    val sessionId: Int,
    val date: String,
    val startTime: String,
    val endTime: String,
    val status: String
)
