package com.example.ble_attendance_backend.dto;

import com.example.ble_attendance_backend.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email must be valid")
        String email,
        @NotBlank(message = "password is required")
        String password,
        // Optional: when set, login is rejected if the account has a different role.
        Role role) {
}
