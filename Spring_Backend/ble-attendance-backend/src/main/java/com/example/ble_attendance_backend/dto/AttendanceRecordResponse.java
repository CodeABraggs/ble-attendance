package com.example.ble_attendance_backend.dto;

import com.example.ble_attendance_backend.entity.AttendanceRecord;
import com.example.ble_attendance_backend.entity.AttendanceStatus;

public record AttendanceRecordResponse(Long studentId, String email, AttendanceStatus status) {
    public static AttendanceRecordResponse from(AttendanceRecord record) {
        return new AttendanceRecordResponse(record.getStudent().getId(), record.getStudent().getEmail(), record.getStatus());
    }

}
