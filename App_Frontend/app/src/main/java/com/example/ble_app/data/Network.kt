package com.example.ble_app.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

interface ApiService {
    // AUTH
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): User

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): User

    // CLASSROOM
    @POST("api/classrooms")
    suspend fun createClassroom(@Body request: CreateClassRequest): Classroom

    @GET("api/classrooms/teacher/{teacherId}")
    suspend fun getTeacherClasses(@Path("teacherId") teacherId: Int): List<Classroom>

    @GET("api/classrooms/student/{studentId}")
    suspend fun getStudentClasses(@Path("studentId") studentId: Int): List<Classroom>

    @POST("api/classrooms/join")
    suspend fun joinClassroom(@Body request: JoinClassRequest): Void

    @GET("api/classrooms/{classroomId}")
    suspend fun getClassroomById(@Path("classroomId") classroomId: Int): Classroom

    @GET("api/classrooms/code/{code}")
    suspend fun getClassroomByCode(@Path("code") code: String): Classroom

    // ATTENDANCE
    @POST("api/attendance/sessions")
    suspend fun createAttendanceSession(@Body request: CreateSessionRequest): AttendanceSession

    @GET("api/classes/{classId}/attendance/sessions")
    suspend fun getSessionsForClassroom(@Path("classId") classId: Int): List<AttendanceSession>

    @GET("api/attendance/sessions/{sessionId}/records")
    suspend fun getAttendanceRecords(@Path("sessionId") sessionId: Int): List<AttendanceRecord>

    @PUT("api/attendance/sessions/{sessionId}/students/{studentId}")
    suspend fun updateAttendanceManually(
        @Path("sessionId") sessionId: Int,
        @Path("studentId") studentId: Int,
        @Body request: UpdateAttendanceRequest
    ): Void

    @GET("api/classes/{classId}/attendance/student/{studentId}")
    suspend fun getStudentAttendanceHistory(
        @Path("classId") classId: Int,
        @Path("studentId") studentId: Int
    ): List<StudentAttendanceHistoryRecord>

    @POST("api/attendance/sessions/{sessionId}/students/{studentId}/mark")
    suspend fun markAttendance(
        @Path("sessionId") sessionId: Int,
        @Path("studentId") studentId: Int,
        @Body request: MarkAttendanceRequest
    ): Void
}

data class RegisterRequest(
    val email: String,
    val password: String,
    val role: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class CreateSessionRequest(
    val classId: Int,
    val date: String,
    val startTime: String,
    val endTime: String
)

data class MarkAttendanceRequest(
    val verification: String = "DUMMY_FACE_VERIFIED"
)

object NetworkConfig {
    // Centralized Base URL
    const val API_BASE_URL = "http://10.20.62.208:8080/"
    
    // BLE Constants
    const val ATTENDANCE_SERVICE_UUID = "12345678-1234-1234-1234-123456789001"
    const val BLE_PAYLOAD_PREFIX = "BLE_ATTENDANCE:"

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(API_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
