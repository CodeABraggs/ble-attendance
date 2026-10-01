package com.example.ble_app.data

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

// Endpoints whose body the app ignores return Unit: a `Void` return type makes Retrofit's suspend adapter
// throw KotlinNullPointerException even when the request succeeded. Endpoints that reply 201/204 with no
// body at all return Response<Unit> (checked with requireSuccess), because Retrofit hands those back as a
// null body, which a plain Unit return type also rejects.
interface ApiService {
    // AUTH
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): LoginResponse

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("api/auth/me")
    suspend fun me(): MeResponse

    @POST("api/auth/logout")
    suspend fun logout(@Header("Authorization") authorization: String): Response<Unit>

    // DEVICE AND FACE
    @PUT("api/devices/me")
    suspend fun bindDevice(@Body request: BindDeviceRequest): Response<Unit>

    @POST("api/face/enroll")
    suspend fun enrollFace(@Body request: EnrollFaceRequest): Response<Unit>

    @DELETE("api/classrooms/{classId}/students/{studentId}/face")
    suspend fun resetStudentFace(@Path("classId") classId: Int, @Path("studentId") studentId: Int): Response<Unit>

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

    @POST("api/attendance/sessions/{sessionId}/challenge")
    suspend fun requestChallenge(
        @Path("sessionId") sessionId: Int,
        @Body request: ChallengeRequest
    ): AttendanceChallenge

    @POST("api/attendance/sessions/{sessionId}/mark")
    suspend fun markAttendance(
        @Path("sessionId") sessionId: Int,
        @Body request: MarkAttendanceRequest
    ): AttendanceRecord

    @GET("api/attendance/sessions/{sessionId}/students/{studentId}/verification")
    suspend fun getVerification(
        @Path("sessionId") sessionId: Int,
        @Path("studentId") studentId: Int
    ): FaceVerification
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
    val expiresAtEpochMillis: Long,
    val faceEnrolled: Boolean
)

data class MeResponse(
    val userId: Int,
    val email: String,
    val role: String,
    val faceEnrolled: Boolean,
    val deviceBound: Boolean
)

data class BindDeviceRequest(
    val publicKey: String
)

// Face sample fields: embedding is base64 of 192 little-endian floats, photo is base64 JPEG,
// signature is the device key's signature over the payload built in Repository.
data class EnrollFaceRequest(
    val embedding: String,
    val photo: String,
    val spoofScoreBp: Int,
    val signature: String,
    // FaceModels.MODEL_VERSION; the server only compares samples and templates of the same version.
    val modelVersion: Int
)

data class ChallengeRequest(
    val beaconCode: Int
)

data class CreateSessionRequest(
    val classId: Int,
    val date: String,
    val startTime: String,
    val endTime: String,
    val zoneId: String
)

data class MarkAttendanceRequest(
    val nonce: String,
    val embedding: String,
    val photo: String,
    val spoofScoreBp: Int,
    val signature: String,
    val modelVersion: Int
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

/** For empty-body endpoints: throws the same HttpException a normal call would on a non-2xx reply. */
fun <T> Response<T>.requireSuccess() {
    if (!isSuccessful) throw HttpException(this)
}
