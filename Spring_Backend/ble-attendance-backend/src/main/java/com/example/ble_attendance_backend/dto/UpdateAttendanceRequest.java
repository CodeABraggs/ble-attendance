package com.example.ble_attendance_backend.dto;

import com.example.ble_attendance_backend.entity.AttendanceStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateAttendanceRequest(@NotNull(message = "status is required") AttendanceStatus status) {
}
