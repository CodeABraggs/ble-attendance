package com.example.ble_attendance_backend.service;

import com.example.ble_attendance_backend.dto.AttendanceHistoryResponse;
import com.example.ble_attendance_backend.dto.AttendanceRecordResponse;
import com.example.ble_attendance_backend.dto.AttendanceSessionRequest;
import com.example.ble_attendance_backend.dto.AttendanceSessionResponse;
import com.example.ble_attendance_backend.dto.MarkAttendanceRequest;
import com.example.ble_attendance_backend.dto.UpdateAttendanceRequest;
import com.example.ble_attendance_backend.entity.AttendanceRecord;
import com.example.ble_attendance_backend.entity.AttendanceSession;
import com.example.ble_attendance_backend.entity.AttendanceStatus;
import com.example.ble_attendance_backend.entity.Classroom;
import com.example.ble_attendance_backend.entity.Role;
import com.example.ble_attendance_backend.entity.User;
import com.example.ble_attendance_backend.exception.BadRequestException;
import com.example.ble_attendance_backend.exception.ResourceNotFoundException;
import com.example.ble_attendance_backend.repository.AttendanceRecordRepository;
import com.example.ble_attendance_backend.repository.AttendanceSessionRepository;
import com.example.ble_attendance_backend.repository.ClassroomMembershipRepository;
import com.example.ble_attendance_backend.repository.ClassroomRepository;
import com.example.ble_attendance_backend.repository.UserRepository;
import java.util.List;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttendanceService {
    private static final String DUMMY_VERIFICATION = "DUMMY_FACE_VERIFIED";
    private static final ZoneId APPLICATION_ZONE = ZoneId.of("Asia/Kolkata");
    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceRecordRepository recordRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomMembershipRepository membershipRepository;
    private final UserRepository userRepository;

    public AttendanceService(AttendanceSessionRepository sessionRepository, AttendanceRecordRepository recordRepository,
                             ClassroomRepository classroomRepository, ClassroomMembershipRepository membershipRepository,
                             UserRepository userRepository) {
        this.sessionRepository = sessionRepository;
        this.recordRepository = recordRepository;
        this.classroomRepository = classroomRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public AttendanceSessionResponse createSession(AttendanceSessionRequest request) {
        if (!request.startTime().isBefore(request.endTime())) {
            throw new BadRequestException("startTime must be before endTime");
        }
        Classroom classroom = requireClassroom(request.classId());
        if (classroom.getTeacher().getRole() != Role.TEACHER) {
            throw new BadRequestException("Classroom owner must be a teacher");
        }
        AttendanceSession session = sessionRepository.save(new AttendanceSession(classroom, request.date(), request.startTime(), request.endTime()));
        membershipRepository.findByClassroomId(classroom.getId()).stream()
            .filter(membership -> !recordRepository.existsBySessionIdAndStudentId(session.getId(), membership.getStudent().getId()))
                .map(membership -> new AttendanceRecord(session, membership.getStudent()))
                .forEach(recordRepository::save);
        return AttendanceSessionResponse.from(session);
    }

    @Transactional(readOnly = true)
    public List<AttendanceSessionResponse> getClassroomSessions(Long classId) {
        requireClassroom(classId);
        return sessionRepository.findByClassroomIdOrderByDateDescStartTimeDesc(classId).stream().map(AttendanceSessionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AttendanceRecordResponse> getRecords(Long sessionId) {
        requireSession(sessionId);
        return recordRepository.findBySessionIdOrderByStudentId(sessionId).stream().map(AttendanceRecordResponse::from).toList();
    }

    @Transactional
    public AttendanceRecordResponse updateRecord(Long sessionId, Long studentId, UpdateAttendanceRequest request) {
        AttendanceRecord record = recordRepository.findBySessionIdAndStudentId(sessionId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance record not found"));
        if (request.status() == AttendanceStatus.LATE) {
            throw new BadRequestException("status must be PRESENT or ABSENT");
        }
        record.setStatus(request.status());
        return AttendanceRecordResponse.from(recordRepository.save(record));
    }

    @Transactional
    public AttendanceRecordResponse markPresent(Long sessionId, Long studentId, MarkAttendanceRequest request) {
        if (!DUMMY_VERIFICATION.equals(request.verification())) {
            throw new BadRequestException("Unsupported attendance verification");
        }
        AttendanceSession session = requireSession(sessionId);
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found: " + studentId));
        if (student.getRole() != Role.STUDENT) {
            throw new BadRequestException("User must have role STUDENT");
        }
        if (!membershipRepository.existsByClassroomIdAndStudentId(session.getClassroom().getId(), studentId)) {
            throw new ResourceNotFoundException("Student is not a member of this classroom");
        }
        if (!isSessionCurrentlyActive(session)) {
            throw new BadRequestException("Attendance session is not currently active");
        }
        AttendanceRecord record = recordRepository.findBySessionIdAndStudentId(sessionId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance record not found"));
        if (record.getStatus() != AttendanceStatus.PRESENT) {
            record.setStatus(AttendanceStatus.PRESENT);
            record = recordRepository.save(record);
        }
        return AttendanceRecordResponse.from(record);
    }

    @Transactional(readOnly = true)
    public List<AttendanceHistoryResponse> getStudentHistory(Long classId, Long studentId) {
        if (!userRepository.existsById(studentId)) {
            throw new ResourceNotFoundException("Student not found: " + studentId);
        }
        if (!membershipRepository.existsByClassroomIdAndStudentId(classId, studentId)) {
            throw new ResourceNotFoundException("Student is not a member of this classroom");
        }
        return recordRepository.findBySessionClassroomIdAndStudentIdOrderBySessionDateDescSessionStartTimeDesc(classId, studentId).stream().map(AttendanceHistoryResponse::from).toList();
    }

    private Classroom requireClassroom(Long classId) {
        return classroomRepository.findById(classId).orElseThrow(() -> new ResourceNotFoundException("Classroom not found: " + classId));
    }

    private AttendanceSession requireSession(Long sessionId) {
        return sessionRepository.findById(sessionId).orElseThrow(() -> new ResourceNotFoundException("Attendance session not found: " + sessionId));
    }

    private boolean isSessionCurrentlyActive(AttendanceSession session) {
        ZonedDateTime now = ZonedDateTime.now(APPLICATION_ZONE);
        LocalDateTime sessionStart = LocalDateTime.of(session.getDate(), session.getStartTime());
        LocalDateTime sessionEnd = LocalDateTime.of(session.getDate(), session.getEndTime());
        LocalDateTime current = now.toLocalDateTime();
        return !current.isBefore(sessionStart) && current.isBefore(sessionEnd);
    }
}