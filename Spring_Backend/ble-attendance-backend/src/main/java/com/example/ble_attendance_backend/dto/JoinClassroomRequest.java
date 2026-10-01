package com.example.ble_attendance_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record JoinClassroomRequest(
        @NotBlank(message = "classroom code is required")
        @Pattern(regexp = "[A-Za-z0-9]{6}", message = "classroom code must be 6 letters or digits")
        String code) {
}
