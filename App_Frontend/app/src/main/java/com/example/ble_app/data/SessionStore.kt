package com.example.ble_app.data

import android.content.Context

data class StoredSession(
    val token: String,
    val user: User,
    val expiresAtEpochMillis: Long
)

/** Persists the login so the user stays signed in until the server-issued token expires (7 days). */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): StoredSession? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val email = prefs.getString(KEY_EMAIL, null)
        val role = prefs.getString(KEY_ROLE, null)
        val userId = prefs.getInt(KEY_USER_ID, -1)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        val faceEnrolled = prefs.getBoolean(KEY_FACE_ENROLLED, false)
        if (email == null || role == null || userId < 0 || expiresAt <= System.currentTimeMillis()) {
            clear()
            return null
        }
        return StoredSession(token, User(userId, email, role, faceEnrolled), expiresAt)
    }

    fun save(session: StoredSession) {
        prefs.edit()
            .putString(KEY_TOKEN, session.token)
            .putInt(KEY_USER_ID, session.user.userId)
            .putString(KEY_EMAIL, session.user.email)
            .putString(KEY_ROLE, session.user.role)
            .putLong(KEY_EXPIRES_AT, session.expiresAtEpochMillis)
            .putBoolean(KEY_FACE_ENROLLED, session.user.faceEnrolled)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        // Excluded from backups in res/xml/backup_rules.xml and data_extraction_rules.xml.
        const val PREFS_NAME = "auth_session"
        const val KEY_TOKEN = "token"
        const val KEY_USER_ID = "user_id"
        const val KEY_EMAIL = "email"
        const val KEY_ROLE = "role"
        const val KEY_EXPIRES_AT = "expires_at"
        const val KEY_FACE_ENROLLED = "face_enrolled"
    }
}
