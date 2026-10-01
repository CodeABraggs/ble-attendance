package com.example.ble_attendance_backend.dto;

import com.example.ble_attendance_backend.entity.AttendanceSession;
import java.time.LocalDate;
import java.time.LocalTime;

public record AttendanceSessionResponse(Long sessionId, Long classId, LocalDate date, LocalTime startTime, LocalTime endTime) {
    public static AttendanceSessionResponse from(AttendanceSession session) {
        return new AttendanceSessionResponse(session.getId(), session.getClassroom().getId(), session.getDate(), session.getStartTime(), session.getEndTime());
    }
}
