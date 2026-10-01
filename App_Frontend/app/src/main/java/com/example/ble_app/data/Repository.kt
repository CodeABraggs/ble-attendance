package com.example.ble_app.data

import android.util.Log
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

    fun setCurrentUser(user: User?) {
        _currentUser.value = user
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
                    404 -> serverMsg ?: "Account does not exist. Create an account first."
                    400 -> serverMsg ?: "Invalid email or password."
                    409 -> serverMsg ?: "An account with this email already exists."
                    else -> serverMsg ?: "Server error (${code}). Please try again."
                }
            }
            else -> e.message ?: "An unexpected error occurred."
        }
    }

    suspend fun login(email: String, password: String): User {
        try {
            val user = NetworkConfig.apiService.login(LoginRequest(email, password))
            _currentUser.value = user
            return user
        } catch (e: Throwable) {
            throw Exception(parseError(e))
        }
    }

    suspend fun register(email: String, password: String, role: String): User {
        try {
            return NetworkConfig.apiService.register(RegisterRequest(email, password, role))
        } catch (e: Throwable) {
            throw Exception(parseError(e))
        }
    }

    fun logout() {
        _currentUser.value = null
        _classrooms.value = emptyList()
    }

    suspend fun fetchClassrooms() {
        val user = _currentUser.value ?: return
        try {
            val list = if (user.role == "TEACHER") {
                NetworkConfig.apiService.getTeacherClasses(user.userId)
            } else {
                NetworkConfig.apiService.getStudentClasses(user.userId)
            }
            _classrooms.value = list
        } catch (e: Throwable) {
            Log.e("Repository", "Failed to fetch classrooms", e)
        }
    }

    suspend fun createClassroom(name: String): Classroom {
        val user = _currentUser.value ?: throw Exception("Not logged in")
        try {
            val classroom = NetworkConfig.apiService.createClassroom(CreateClassRequest(user.userId, name))
            fetchClassrooms()
            return classroom
        } catch (e: Throwable) {
            throw Exception(parseError(e))
        }
    }

    suspend fun getClassroomByCode(code: String): Classroom {
        try {
            return NetworkConfig.apiService.getClassroomByCode(code)
        } catch (e: Throwable) {
            throw Exception(parseError(e))
        }
    }

    suspend fun joinClassroom(code: String) {
        val user = _currentUser.value ?: throw Exception("Not logged in")
        try {
            NetworkConfig.apiService.joinClassroom(JoinClassRequest(user.userId, code))
            fetchClassrooms()
        } catch (e: Throwable) {
            throw Exception(parseError(e))
        }
    }

    suspend fun createAttendanceSession(classId: Int, date: String, startTime: String, endTime: String): AttendanceSession {
        try {
            return NetworkConfig.apiService.createAttendanceSession(CreateSessionRequest(classId, date, startTime, endTime))
        } catch (e: Throwable) {
            throw Exception(parseError(e))
        }
    }

    suspend fun getSessionsForClassroom(classId: Int): List<AttendanceSession> {
        try {
            return NetworkConfig.apiService.getSessionsForClassroom(classId)
        } catch (e: Throwable) {
            throw Exception(parseError(e))
        }
    }

    suspend fun getAttendanceRecords(sessionId: Int): List<AttendanceRecord> {
        try {
            return NetworkConfig.apiService.getAttendanceRecords(sessionId)
        } catch (e: Throwable) {
            throw Exception(parseError(e))
        }
    }

    suspend fun updateAttendanceManually(sessionId: Int, studentId: Int, status: String) {
        try {
            NetworkConfig.apiService.updateAttendanceManually(sessionId, studentId, UpdateAttendanceRequest(status))
        } catch (e: Throwable) {
            throw Exception(parseError(e))
        }
    }

    suspend fun getStudentAttendanceHistory(classId: Int): List<StudentAttendanceHistoryRecord> {
        val user = _currentUser.value ?: return emptyList()
        try {
            return NetworkConfig.apiService.getStudentAttendanceHistory(classId, user.userId)
        } catch (e: Throwable) {
            throw Exception(parseError(e))
        }
    }

    suspend fun markAttendance(sessionId: Int) {
        val user = _currentUser.value ?: throw Exception("Not logged in")
        try {
            NetworkConfig.apiService.markAttendance(sessionId, user.userId, MarkAttendanceRequest())
        } catch (e: Throwable) {
            throw Exception(parseError(e))
        }
    }
}
