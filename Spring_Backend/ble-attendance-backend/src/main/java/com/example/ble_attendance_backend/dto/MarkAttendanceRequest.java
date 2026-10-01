package com.example.ble_attendance_backend.dto;

import jakarta.validation.constraints.NotBlank;

public record MarkAttendanceRequest(
        @NotBlank(message = "verification is required") String verification) {
}