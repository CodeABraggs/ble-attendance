package com.example.ble_app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
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
            _currentUser.value = saved.user
        }
    }

    fun authToken(): String? = token

    /** Called by the HTTP layer when the server rejects [rejectedToken] (expired or revoked). */
    fun onUnauthorized(rejectedToken: String) {
        if (token == rejectedToken) clearSession()
    }

    private fun startSession(response: LoginResponse): User {
        val user = User(response.userId, response.email, response.role)
        token = response.token
        sessionStore?.save(StoredSession(response.token, user, response.expiresAtEpochMillis))
        _currentUser.value = user
        return user
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

    suspend fun login(email: String, password: String, role: String): User = apiCall {
        startSession(NetworkConfig.apiService.login(LoginRequest(email.trim(), password, role)))
    }

    /** Registers and signs the new user in. */
    suspend fun register(email: String, password: String, role: String): User = apiCall {
        startSession(NetworkConfig.apiService.register(RegisterRequest(email.trim(), password, role)))
    }

    /** Checks the saved token with the server; a 401 signs the user out via the HTTP interceptor. */
    suspend fun validateSession() {
        if (token == null) return
        try {
            NetworkConfig.apiService.me()
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

    suspend fun markAttendance(sessionId: Int, beaconCode: Int) = apiCall {
        NetworkConfig.apiService.markAttendance(sessionId, MarkAttendanceRequest(beaconCode))
    }
}
