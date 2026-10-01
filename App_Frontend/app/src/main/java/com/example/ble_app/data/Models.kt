package com.example.ble_app.data

enum class UserRole {
    TEACHER, STUDENT
}

data class User(
    val userId: Int,
    val email: String,
    val role: String
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
    val status: String
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
