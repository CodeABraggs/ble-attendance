package com.example.ble_attendance_backend.controller;

import com.example.ble_attendance_backend.dto.AttendanceHistoryResponse;
import com.example.ble_attendance_backend.dto.AttendanceRecordResponse;
import com.example.ble_attendance_backend.dto.AttendanceSessionRequest;
import com.example.ble_attendance_backend.dto.AttendanceSessionResponse;
import com.example.ble_attendance_backend.dto.MarkAttendanceRequest;
import com.example.ble_attendance_backend.dto.UpdateAttendanceRequest;
import com.example.ble_attendance_backend.security.AuthenticatedUser;
import com.example.ble_attendance_backend.service.AttendanceService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AttendanceController {
    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PostMapping("/attendance/sessions")
    public ResponseEntity<AttendanceSessionResponse> createSession(AuthenticatedUser caller, @Valid @RequestBody AttendanceSessionRequest request) {
        return ResponseEntity.status(201).body(attendanceService.createSession(caller, request));
    }

    @GetMapping("/classes/{classId}/attendance/sessions")
    public List<AttendanceSessionResponse> getClassroomSessions(AuthenticatedUser caller, @PathVariable Long classId) {
        return attendanceService.getClassroomSessions(caller, classId);
    }

    @GetMapping("/attendance/sessions/{sessionId}/records")
    public List<AttendanceRecordResponse> getRecords(AuthenticatedUser caller, @PathVariable Long sessionId) {
        return attendanceService.getRecords(caller, sessionId);
    }

    @PutMapping("/attendance/sessions/{sessionId}/students/{studentId}")
    public AttendanceRecordResponse updateRecord(AuthenticatedUser caller, @PathVariable Long sessionId, @PathVariable Long studentId,
                                                 @Valid @RequestBody UpdateAttendanceRequest request) {
        return attendanceService.updateRecord(caller, sessionId, studentId, request);
    }

    /** Marks the calling student present; requires a current beacon code from the teacher's device. */
    @PostMapping("/attendance/sessions/{sessionId}/mark")
    public AttendanceRecordResponse markPresent(AuthenticatedUser caller, @PathVariable Long sessionId,
                                                @Valid @RequestBody MarkAttendanceRequest request) {
        return attendanceService.markPresent(caller, sessionId, request);
    }

    @GetMapping("/classes/{classId}/attendance/me")
    public List<AttendanceHistoryResponse> getMyHistory(AuthenticatedUser caller, @PathVariable Long classId) {
        return attendanceService.getMyHistory(caller, classId);
    }
}
