package com.example.ble_attendance_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateClassroomRequest(
        @NotBlank(message = "classroom name is required")
        @Size(max = 150, message = "classroom name must be at most 150 characters")
        String name) {
}
