package com.example.ble_attendance_backend.dto;

import com.example.ble_attendance_backend.entity.Role;

public record MeResponse(Long userId, String email, Role role, boolean faceEnrolled, boolean deviceBound) {
}
