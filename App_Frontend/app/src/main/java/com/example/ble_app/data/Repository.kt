package com.example.ble_app.data

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.ble_app.face.CapturedFace
import com.example.ble_app.security.DeviceKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import retrofit2.HttpException
import org.json.JSONObject
import java.io.IOException

object Repository {
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    private val _classrooms = MutableStateFlow<List<Classroom>>(emptyList())
    val classrooms: StateFlow<List<Classroom>> = _classrooms

    private var sessionStore: SessionStore? = null

    @Volatile
    private var token: String? = null

    /** Restores a saved login. Safe to call more than once. */
    fun init(context: Context) {
        if (sessionStore != null) return
        val store = SessionStore(context.applicationContext)
        sessionStore = store
        store.load()?.let { saved ->
            token = saved.token
            tokenExpiresAt = saved.expiresAtEpochMillis
            _currentUser.value = saved.user
        }
    }

    fun authToken(): String? = token

    /** Called by the HTTP layer when the server rejects [rejectedToken] (expired or revoked). */
    fun onUnauthorized(rejectedToken: String) {
        if (token == rejectedToken) clearSession()
    }

    private var tokenExpiresAt = 0L

    private fun startSession(response: LoginResponse): User {
        val user = User(response.userId, response.email, response.role, response.faceEnrolled)
        token = response.token
        tokenExpiresAt = response.expiresAtEpochMillis
        sessionStore?.save(StoredSession(response.token, user, response.expiresAtEpochMillis))
        _currentUser.value = user
        return user
    }

    private fun updateUser(user: User) {
        val currentToken = token ?: return
        _currentUser.value = user
        sessionStore?.save(StoredSession(currentToken, user, tokenExpiresAt))
    }

    /**
     * Links the account to this phone's hardware key. A phone can belong to only one student, so if the
     * server refuses (the phone is linked to someone else) the login is undone.
     */
    private suspend fun bindDeviceOrLogout(user: User) {
        if (user.role != "STUDENT") return
        try {
            // Generating the hardware key the first time can take a moment; keep it off the main thread.
            val publicKey = withContext(Dispatchers.Default) { DeviceKey.publicKeyBase64() }
            NetworkConfig.apiService.bindDevice(BindDeviceRequest(publicKey))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val message = parseError(e)
            logout()
            throw Exception(message)
        }
    }

    private fun clearSession() {
        token = null
        sessionStore?.clear()
        _currentUser.value = null
        _classrooms.value = emptyList()
    }

    fun parseError(e: Throwable): String {
        // Print the full stack trace to Logcat for debug visibility
        Log.e("RepositoryNetworkError", "Actual network exception: ${e.javaClass.simpleName} - ${e.message}", e)

        return when (e) {
            is IOException -> "Cannot connect to server. Make sure Spring Boot is running. Details: ${e.localizedMessage}"
            is HttpException -> {
                val code = e.code()
                val errorBody = e.response()?.errorBody()?.string()
                val serverMsg = try {
                    if (!errorBody.isNullOrEmpty()) {
                        val json = JSONObject(errorBody)
                        if (json.has("message")) json.getString("message") else null
                    } else null
                } catch (ex: Exception) {
                    null
                }

                when (code) {
                    401 -> serverMsg ?: "Your session has expired. Please log in again."
                    403 -> serverMsg ?: "You are not allowed to do that."
                    404 -> serverMsg ?: "Not found."
                    400 -> serverMsg ?: "Invalid request."
                    409 -> serverMsg ?: "An account with this email already exists."
                    else -> serverMsg ?: "Server error (${code}). Please try again."
                }
            }
            else -> e.message ?: "An unexpected error occurred."
        }
    }

    // Converts failures into user-facing messages, but lets coroutine cancellation propagate.
    private suspend fun <T> apiCall(block: suspend () -> T): T {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw Exception(parseError(e))
        }
    }

    suspend fun login(email: String, password: String, role: String): User {
        val user = apiCall { startSession(NetworkConfig.apiService.login(LoginRequest(email.trim(), password, role))) }
        bindDeviceOrLogout(user)
        return user
    }

    /** Registers and signs the new user in. */
    suspend fun register(email: String, password: String, role: String): User {
        val user = apiCall { startSession(NetworkConfig.apiService.register(RegisterRequest(email.trim(), password, role))) }
        bindDeviceOrLogout(user)
        return user
    }

    /** Checks the saved token with the server; a 401 signs the user out via the HTTP interceptor. */
    suspend fun validateSession() {
        if (token == null) return
        try {
            val me = NetworkConfig.apiService.me()
            _currentUser.value?.let { user -> updateUser(user.copy(faceEnrolled = me.faceEnrolled)) }
            if (!me.deviceBound) _currentUser.value?.let { bindDeviceOrLogout(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Offline: keep the saved session until the server can be reached.
            Log.w("Repository", "Could not validate saved session", e)
        }
    }

    suspend fun logout() {
        val oldToken = token
        clearSession()
        if (oldToken != null) {
            try {
                NetworkConfig.apiService.logout("Bearer $oldToken")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w("Repository", "Server logout failed; token will expire on its own", e)
            }
        }
    }

    suspend fun fetchClassrooms() {
        if (_currentUser.value == null) return
        try {
            _classrooms.value = NetworkConfig.apiService.getMyClassrooms()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e("Repository", "Failed to fetch classrooms", e)
        }
    }

    suspend fun createClassroom(name: String): Classroom = apiCall {
        val classroom = NetworkConfig.apiService.createClassroom(CreateClassRequest(name))
        fetchClassrooms()
        classroom
    }

    suspend fun getClassroomByCode(code: String): Classroom = apiCall {
        NetworkConfig.apiService.getClassroomByCode(code)
    }

    suspend fun joinClassroom(code: String) = apiCall {
        NetworkConfig.apiService.joinClassroom(JoinClassRequest(code))
        fetchClassrooms()
    }

    suspend fun createAttendanceSession(classId: Int, date: String, startTime: String, endTime: String, zoneId: String): AttendanceSession = apiCall {
        NetworkConfig.apiService.createAttendanceSession(CreateSessionRequest(classId, date, startTime, endTime, zoneId))
    }

    suspend fun getSessionsForClassroom(classId: Int): List<AttendanceSession> = apiCall {
        NetworkConfig.apiService.getSessionsForClassroom(classId)
    }

    suspend fun getAttendanceRecords(sessionId: Int): List<AttendanceRecord> = apiCall {
        NetworkConfig.apiService.getAttendanceRecords(sessionId)
    }

    suspend fun updateAttendanceManually(sessionId: Int, studentId: Int, status: String) = apiCall {
        NetworkConfig.apiService.updateAttendanceManually(sessionId, studentId, UpdateAttendanceRequest(status))
    }

    suspend fun getStudentAttendanceHistory(classId: Int): List<StudentAttendanceHistoryRecord> = apiCall {
        NetworkConfig.apiService.getMyAttendanceHistory(classId)
    }

    /** One-time face enrollment with a sample from the liveness check, signed by this phone's key. */
    suspend fun enrollFace(face: CapturedFace) {
        val user = _currentUser.value ?: throw Exception("Not logged in")
        val sample = SignedSample(face)
        apiCall {
            NetworkConfig.apiService.enrollFace(
                EnrollFaceRequest(
                    embedding = sample.embedding,
                    photo = sample.photo,
                    spoofScoreBp = face.spoofScoreBp,
                    signature = DeviceKey.sign("ENROLL|${user.userId}|${sample.digest}")
                )
            )
        }
        updateUser(user.copy(faceEnrolled = true))
    }

    /** Step 1 of marking attendance: trade the beacon code for a liveness challenge. */
    suspend fun requestChallenge(sessionId: Int, beaconCode: Int): AttendanceChallenge = apiCall {
        NetworkConfig.apiService.requestChallenge(sessionId, ChallengeRequest(beaconCode))
    }

    /** Step 2: send the face sample captured for the challenge. Returns the resulting record status. */
    suspend fun markAttendance(sessionId: Int, nonce: String, face: CapturedFace): AttendanceRecord {
        val sample = SignedSample(face)
        return apiCall {
            NetworkConfig.apiService.markAttendance(
                sessionId,
                MarkAttendanceRequest(
                    nonce = nonce,
                    embedding = sample.embedding,
                    photo = sample.photo,
                    spoofScoreBp = face.spoofScoreBp,
                    signature = DeviceKey.sign("MARK|$sessionId|$nonce|${sample.digest}")
                )
            )
        }
    }

    suspend fun getVerification(sessionId: Int, studentId: Int): FaceVerification = apiCall {
        NetworkConfig.apiService.getVerification(sessionId, studentId)
    }

    suspend fun resetStudentFace(classId: Int, studentId: Int) = apiCall {
        NetworkConfig.apiService.resetStudentFace(classId, studentId)
    }

    /** Encodes a face sample and the digest that is signed (must match FaceService.sampleDigest on the server). */
    private class SignedSample(face: CapturedFace) {
        private val embeddingBytes = face.embeddingBytes()
        val embedding: String = Base64.encodeToString(embeddingBytes, Base64.NO_WRAP)
        val photo: String = Base64.encodeToString(face.photoJpeg, Base64.NO_WRAP)
        val digest = "${DeviceKey.sha256Hex(embeddingBytes)}|${DeviceKey.sha256Hex(face.photoJpeg)}|${face.spoofScoreBp}"
    }
}
