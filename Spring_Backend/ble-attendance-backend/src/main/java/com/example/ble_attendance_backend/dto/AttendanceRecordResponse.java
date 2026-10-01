package com.example.ble_attendance_backend.dto;

import com.example.ble_attendance_backend.entity.AttendanceRecord;
import com.example.ble_attendance_backend.entity.AttendanceStatus;

/** hasVerification: a face-verification photo exists for the teacher to review. */
public record AttendanceRecordResponse(Long studentId, String email, AttendanceStatus status, boolean hasVerification) {
    public static AttendanceRecordResponse from(AttendanceRecord record, boolean hasVerification) {
        return new AttendanceRecordResponse(record.getStudent().getId(), record.getStudent().getEmail(), record.getStatus(), hasVerification);
    }
}
