package com.example.ble_attendance_backend.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

public record AttendanceSessionRequest(
        @NotNull(message = "classId is required") Long classId,
        @NotNull(message = "date is required") LocalDate date,
        @NotNull(message = "startTime is required") LocalTime startTime,
        @NotNull(message = "endTime is required") LocalTime endTime) {
}
