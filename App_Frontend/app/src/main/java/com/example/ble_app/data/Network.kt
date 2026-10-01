package com.example.ble_app.data

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

// Endpoints with no response body the app needs return Unit: a `Void` return type makes Retrofit's
// suspend adapter throw KotlinNullPointerException even when the request succeeded.
interface ApiService {
    // AUTH
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): LoginResponse

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("api/auth/me")
    suspend fun me(): Unit

    @POST("api/auth/logout")
    suspend fun logout(@Header("Authorization") authorization: String): Unit

    // CLASSROOM (the caller is identified by the bearer token)
    @POST("api/classrooms")
    suspend fun createClassroom(@Body request: CreateClassRequest): Classroom

    @GET("api/classrooms")
    suspend fun getMyClassrooms(): List<Classroom>

    @POST("api/classrooms/join")
    suspend fun joinClassroom(@Body request: JoinClassRequest): Unit

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
    ): Unit

    @GET("api/classes/{classId}/attendance/me")
    suspend fun getMyAttendanceHistory(@Path("classId") classId: Int): List<StudentAttendanceHistoryRecord>

    @POST("api/attendance/sessions/{sessionId}/mark")
    suspend fun markAttendance(
        @Path("sessionId") sessionId: Int,
        @Body request: MarkAttendanceRequest
    ): Unit
}

data class RegisterRequest(
    val email: String,
    val password: String,
    val role: String
)

data class LoginRequest(
    val email: String,
    val password: String,
    val role: String
)

data class LoginResponse(
    val userId: Int,
    val email: String,
    val role: String,
    val token: String,
    val expiresAtEpochMillis: Long
)

data class CreateSessionRequest(
    val classId: Int,
    val date: String,
    val startTime: String,
    val endTime: String,
    val zoneId: String
)

data class MarkAttendanceRequest(
    val beaconCode: Int
)

object NetworkConfig {
    // Centralized Base URL
    const val API_BASE_URL = "http://10.20.62.208:8080/"

    // BLE Constants
    const val ATTENDANCE_SERVICE_UUID = "12345678-1234-1234-1234-123456789001"

    // Attaches the saved token and signs the user out when the server rejects it.
    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val token = Repository.authToken()
        val request = if (token != null && original.header("Authorization") == null) {
            original.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            original
        }
        val response = chain.proceed(request)
        if (response.code() == 401 && token != null && request.header("Authorization") == "Bearer $token") {
            Repository.onUnauthorized(token)
        }
        response
    }

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(API_BASE_URL)
            .client(OkHttpClient.Builder().addInterceptor(authInterceptor).build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
