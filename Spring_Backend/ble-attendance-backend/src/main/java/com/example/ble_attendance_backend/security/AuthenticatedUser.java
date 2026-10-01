package com.example.ble_attendance_backend.security;

import com.example.ble_attendance_backend.entity.Role;
import com.example.ble_attendance_backend.entity.User;

/** The caller resolved from the bearer token. Controllers receive it as a method parameter. */
public record AuthenticatedUser(Long id, String email, Role role) {
    public static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole());
    }
}
