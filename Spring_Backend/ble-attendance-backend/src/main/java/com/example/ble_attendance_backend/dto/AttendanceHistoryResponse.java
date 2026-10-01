package com.example.ble_attendance_backend.dto;

import com.example.ble_attendance_backend.entity.AttendanceRecord;
import com.example.ble_attendance_backend.entity.AttendanceStatus;
import java.time.LocalDate;
import java.time.LocalTime;

public record AttendanceHistoryResponse(Long sessionId, LocalDate date, LocalTime startTime, LocalTime endTime, AttendanceStatus status) {
    public static AttendanceHistoryResponse from(AttendanceRecord record) {
        return new AttendanceHistoryResponse(record.getSession().getId(), record.getSession().getDate(), record.getSession().getStartTime(), record.getSession().getEndTime(), record.getStatus());
    }
}