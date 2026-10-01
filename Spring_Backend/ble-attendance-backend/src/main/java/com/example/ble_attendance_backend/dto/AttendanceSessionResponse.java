package com.example.ble_attendance_backend.dto;

import com.example.ble_attendance_backend.entity.AttendanceSession;
import java.time.LocalDate;
import java.time.LocalTime;

/** Only returned to the classroom's teacher, so it can carry the beacon secret. */
public record AttendanceSessionResponse(Long sessionId, Long classId, LocalDate date, LocalTime startTime, LocalTime endTime,
                                        String zoneId, long startEpochMillis, long endEpochMillis, String beaconSecret) {
    public static AttendanceSessionResponse from(AttendanceSession session) {
        return new AttendanceSessionResponse(session.getId(), session.getClassroom().getId(), session.getDate(),
                session.getStartTime(), session.getEndTime(), session.getZone().getId(),
                session.getStartInstant().toEpochMilli(), session.getEndInstant().toEpochMilli(), session.getBeaconSecret());
    }
}
