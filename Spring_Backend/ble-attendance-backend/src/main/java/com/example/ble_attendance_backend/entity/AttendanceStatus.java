package com.example.ble_attendance_backend.entity;

public enum AttendanceStatus {
    PRESENT,
    ABSENT,
    LATE,
    // Face match was borderline (or looked like a replay); the teacher must approve or reject it.
    PENDING_REVIEW
}