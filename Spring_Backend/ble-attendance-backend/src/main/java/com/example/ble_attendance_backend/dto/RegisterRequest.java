package com.example.ble_attendance_backend.dto;

import com.example.ble_attendance_backend.entity.Role;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email must be valid")
        String email,
        @NotBlank(message = "password is required")
        @Size(min = 8, max = 120, message = "password must be between 8 and 120 characters")
        String password,
        @NotNull(message = "role is required")
        Role role) {
    @AssertTrue(message = "email does not match the selected role")
    public boolean isEmailAllowedForRole() {
        if (email == null || role == null) {
            return true;
        }
        return switch (role) {
            case TEACHER -> email.matches("(?i)^teacher(10|[1-9])@gmail\\.com$");
            case STUDENT -> email.matches("(?i)^student([1-9][0-9]?|100)@gmail\\.com$");
        };
    }
}
