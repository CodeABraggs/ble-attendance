package com.example.ble_attendance_backend.controller;

import com.example.ble_attendance_backend.dto.AttendanceHistoryResponse;
import com.example.ble_attendance_backend.dto.AttendanceRecordResponse;
import com.example.ble_attendance_backend.dto.AttendanceSessionRequest;
import com.example.ble_attendance_backend.dto.AttendanceSessionResponse;
import com.example.ble_attendance_backend.dto.MarkAttendanceRequest;
import com.example.ble_attendance_backend.dto.UpdateAttendanceRequest;
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
    public ResponseEntity<AttendanceSessionResponse> createSession(@Valid @RequestBody AttendanceSessionRequest request) {
        return ResponseEntity.status(201).body(attendanceService.createSession(request));
    }

    @GetMapping("/classes/{classId}/attendance/sessions")
    public List<AttendanceSessionResponse> getClassroomSessions(@PathVariable Long classId) {
        return attendanceService.getClassroomSessions(classId);
    }

    @GetMapping("/attendance/sessions/{sessionId}/records")
    public List<AttendanceRecordResponse> getRecords(@PathVariable Long sessionId) {
        return attendanceService.getRecords(sessionId);
    }

    @PutMapping("/attendance/sessions/{sessionId}/students/{studentId}")
    public AttendanceRecordResponse updateRecord(@PathVariable Long sessionId, @PathVariable Long studentId,
                                                  @Valid @RequestBody UpdateAttendanceRequest request) {
        return attendanceService.updateRecord(sessionId, studentId, request);
    }

    @PostMapping("/attendance/sessions/{sessionId}/students/{studentId}/mark")
    public AttendanceRecordResponse markPresent(@PathVariable Long sessionId, @PathVariable Long studentId,
                                                 @Valid @RequestBody MarkAttendanceRequest request) {
        return attendanceService.markPresent(sessionId, studentId, request);
    }

    @GetMapping("/classes/{classId}/attendance/student/{studentId}")
    public List<AttendanceHistoryResponse> getStudentHistory(@PathVariable Long classId, @PathVariable Long studentId) {
        return attendanceService.getStudentHistory(classId, studentId);
    }
}
