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
import com.example.ble_attendance_backend.exception.ForbiddenException;
import com.example.ble_attendance_backend.exception.ResourceNotFoundException;
import com.example.ble_attendance_backend.repository.AttendanceRecordRepository;
import com.example.ble_attendance_backend.repository.AttendanceSessionRepository;
import com.example.ble_attendance_backend.repository.ClassroomMembershipRepository;
import com.example.ble_attendance_backend.repository.ClassroomRepository;
import com.example.ble_attendance_backend.repository.UserRepository;
import com.example.ble_attendance_backend.security.AuthenticatedUser;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttendanceService {
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
    public AttendanceSessionResponse createSession(AuthenticatedUser caller, AttendanceSessionRequest request) {
        if (!request.startTime().isBefore(request.endTime())) {
            throw new BadRequestException("startTime must be before endTime");
        }
        ZoneId zone;
        try {
            zone = ZoneId.of(request.zoneId());
        } catch (DateTimeException exception) {
            throw new BadRequestException("zoneId is not a valid time zone");
        }
        Classroom classroom = requireOwnedClassroom(caller, request.classId());
        AttendanceSession session = sessionRepository.save(new AttendanceSession(
                classroom, request.date(), request.startTime(), request.endTime(), zone, BeaconCodes.newSecret()));
        recordRepository.saveAll(membershipRepository.findByClassroomId(classroom.getId()).stream()
                .map(membership -> new AttendanceRecord(session, membership.getStudent()))
                .toList());
        return AttendanceSessionResponse.from(session);
    }

    @Transactional(readOnly = true)
    public List<AttendanceSessionResponse> getClassroomSessions(AuthenticatedUser caller, Long classId) {
        requireOwnedClassroom(caller, classId);
        return sessionRepository.findByClassroomIdOrderByDateDescStartTimeDesc(classId).stream().map(AttendanceSessionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AttendanceRecordResponse> getRecords(AuthenticatedUser caller, Long sessionId) {
        requireOwnedSession(caller, sessionId);
        return recordRepository.findBySessionIdOrderByStudentId(sessionId).stream().map(AttendanceRecordResponse::from).toList();
    }

    @Transactional
    public AttendanceRecordResponse updateRecord(AuthenticatedUser caller, Long sessionId, Long studentId, UpdateAttendanceRequest request) {
        requireOwnedSession(caller, sessionId);
        if (request.status() == AttendanceStatus.LATE) {
            throw new BadRequestException("status must be PRESENT or ABSENT");
        }
        AttendanceRecord record = recordRepository.findBySessionIdAndStudentId(sessionId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance record not found"));
        record.setStatus(request.status());
        return AttendanceRecordResponse.from(recordRepository.save(record));
    }

    @Transactional
    public AttendanceRecordResponse markPresent(AuthenticatedUser caller, Long sessionId, MarkAttendanceRequest request) {
        if (caller.role() != Role.STUDENT) {
            throw new ForbiddenException("Only a STUDENT can mark attendance");
        }
        AttendanceSession session = requireSession(sessionId);
        if (!membershipRepository.existsByClassroomIdAndStudentId(session.getClassroom().getId(), caller.id())) {
            throw new ForbiddenException("You are not a member of this classroom");
        }
        Instant now = Instant.now();
        if (now.isBefore(session.getStartInstant()) || !now.isBefore(session.getEndInstant())) {
            throw new BadRequestException("Attendance session is not currently active");
        }
        if (!BeaconCodes.verify(session.getBeaconSecret(), session.getId(), request.beaconCode(), now)) {
            throw new BadRequestException("Beacon code is invalid or expired. Scan again near your teacher's device");
        }
        // Students who joined after the session was created have no record yet.
        AttendanceRecord record = recordRepository.findBySessionIdAndStudentId(sessionId, caller.id())
                .orElseGet(() -> new AttendanceRecord(session, userRepository.getReferenceById(caller.id())));
        if (record.getId() == null || record.getStatus() != AttendanceStatus.PRESENT) {
            record.setStatus(AttendanceStatus.PRESENT);
            record = recordRepository.save(record);
        }
        return AttendanceRecordResponse.from(record);
    }

    @Transactional(readOnly = true)
    public List<AttendanceHistoryResponse> getMyHistory(AuthenticatedUser caller, Long classId) {
        if (caller.role() != Role.STUDENT) {
            throw new ForbiddenException("Only a STUDENT has attendance history");
        }
        if (!membershipRepository.existsByClassroomIdAndStudentId(classId, caller.id())) {
            throw new ForbiddenException("You are not a member of this classroom");
        }
        return recordRepository.findBySessionClassroomIdAndStudentIdOrderBySessionDateDescSessionStartTimeDesc(classId, caller.id())
                .stream().map(AttendanceHistoryResponse::from).toList();
    }

    /** Gives a newly joined student an ABSENT record in every session of the class that hasn't ended yet. */
    @Transactional
    public void addStudentToOpenSessions(Classroom classroom, User student) {
        Instant now = Instant.now();
        recordRepository.saveAll(sessionRepository.findByClassroomIdOrderByDateDescStartTimeDesc(classroom.getId()).stream()
                .filter(session -> session.getEndInstant().isAfter(now))
                .filter(session -> !recordRepository.existsBySessionIdAndStudentId(session.getId(), student.getId()))
                .map(session -> new AttendanceRecord(session, student))
                .toList());
    }

    private Classroom requireOwnedClassroom(AuthenticatedUser caller, Long classId) {
        Classroom classroom = classroomRepository.findById(classId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found: " + classId));
        if (caller.role() != Role.TEACHER || !classroom.getTeacher().getId().equals(caller.id())) {
            throw new ForbiddenException("Only the classroom's teacher can do this");
        }
        return classroom;
    }

    private AttendanceSession requireOwnedSession(AuthenticatedUser caller, Long sessionId) {
        AttendanceSession session = requireSession(sessionId);
        requireOwnedClassroom(caller, session.getClassroom().getId());
        return session;
    }

    private AttendanceSession requireSession(Long sessionId) {
        return sessionRepository.findById(sessionId).orElseThrow(() -> new ResourceNotFoundException("Attendance session not found: " + sessionId));
    }
}
