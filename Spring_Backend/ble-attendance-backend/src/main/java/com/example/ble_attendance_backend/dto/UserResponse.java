package com.example.ble_attendance_backend.dto;

import com.example.ble_attendance_backend.entity.Role;
import com.example.ble_attendance_backend.entity.User;

public record UserResponse(Long userId, String email, Role role) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole());
    }
}