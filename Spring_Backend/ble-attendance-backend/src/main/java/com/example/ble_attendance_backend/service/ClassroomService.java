package com.example.ble_attendance_backend.service;

import com.example.ble_attendance_backend.dto.ClassroomResponse;
import com.example.ble_attendance_backend.entity.Classroom;
import com.example.ble_attendance_backend.entity.ClassroomMembership;
import com.example.ble_attendance_backend.entity.Role;
import com.example.ble_attendance_backend.entity.User;
import com.example.ble_attendance_backend.exception.BadRequestException;
import com.example.ble_attendance_backend.exception.ConflictException;
import com.example.ble_attendance_backend.exception.ForbiddenException;
import com.example.ble_attendance_backend.exception.ResourceNotFoundException;
import com.example.ble_attendance_backend.repository.ClassroomMembershipRepository;
import com.example.ble_attendance_backend.repository.ClassroomRepository;
import com.example.ble_attendance_backend.security.AuthenticatedUser;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassroomService {
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int MAX_CODE_ATTEMPTS = 10;
    private final SecureRandom random = new SecureRandom();
    private final ClassroomRepository classroomRepository;
    private final ClassroomMembershipRepository membershipRepository;
    private final UserService userService;
    private final AttendanceService attendanceService;

    public ClassroomService(ClassroomRepository classroomRepository,
                            ClassroomMembershipRepository membershipRepository,
                            UserService userService,
                            AttendanceService attendanceService) {
        this.classroomRepository = classroomRepository;
        this.membershipRepository = membershipRepository;
        this.userService = userService;
        this.attendanceService = attendanceService;
    }

    @Transactional
    public ClassroomResponse createClassroom(AuthenticatedUser caller, String name) {
        requireRole(caller, Role.TEACHER);
        User teacher = userService.requireUser(caller.id());
        Classroom classroom = classroomRepository.save(new Classroom(name.trim(), generateUniqueCode(), teacher));
        return ClassroomResponse.from(classroom, membershipRepository);
    }

    @Transactional(readOnly = true)
    public List<ClassroomResponse> getMyClassrooms(AuthenticatedUser caller) {
        List<Classroom> classrooms = caller.role() == Role.TEACHER
                ? classroomRepository.findByTeacherIdOrderByCreatedAtDesc(caller.id())
                : membershipRepository.findByStudentIdOrderByJoinedAtDesc(caller.id()).stream()
                        .map(ClassroomMembership::getClassroom)
                        .toList();
        return classrooms.stream().map(classroom -> ClassroomResponse.from(classroom, membershipRepository)).toList();
    }

    @Transactional(readOnly = true)
    public ClassroomResponse getClassroom(AuthenticatedUser caller, Long classroomId) {
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found: " + classroomId));
        boolean allowed = caller.role() == Role.TEACHER
                ? classroom.getTeacher().getId().equals(caller.id())
                : membershipRepository.existsByClassroomIdAndStudentId(classroomId, caller.id());
        if (!allowed) {
            throw new ForbiddenException("You do not have access to this classroom");
        }
        return ClassroomResponse.from(classroom, membershipRepository);
    }

    @Transactional(readOnly = true)
    public ClassroomResponse findByCode(AuthenticatedUser caller, String code) {
        requireRole(caller, Role.STUDENT);
        String normalizedCode = normalizeCode(code);
        return classroomRepository.findByCodeIgnoreCase(normalizedCode)
                .map(classroom -> ClassroomResponse.from(classroom, membershipRepository))
                .orElseThrow(() -> new ResourceNotFoundException("No classroom found for code: " + normalizedCode));
    }

    @Transactional
    public ClassroomResponse joinClassroom(AuthenticatedUser caller, String code) {
        requireRole(caller, Role.STUDENT);
        User student = userService.requireUser(caller.id());
        Classroom classroom = classroomRepository.findByCodeIgnoreCase(normalizeCode(code))
                .orElseThrow(() -> new ResourceNotFoundException("No classroom found for that code"));
        if (membershipRepository.existsByClassroomIdAndStudentId(classroom.getId(), student.getId())) {
            throw new ConflictException("Student is already a member of this classroom");
        }
        membershipRepository.save(new ClassroomMembership(classroom, student));
        attendanceService.addStudentToOpenSessions(classroom, student);
        return ClassroomResponse.from(classroom, membershipRepository);
    }

    private void requireRole(AuthenticatedUser caller, Role role) {
        if (caller.role() != role) {
            throw new ForbiddenException("Only a " + role + " can do this");
        }
    }

    private String normalizeCode(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9]{6}")) {
            throw new BadRequestException("Classroom code must be 6 letters or digits");
        }
        return normalized;
    }

    // Checks before inserting: a unique-constraint failure would mark the transaction rollback-only.
    private String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = generateCode();
            if (classroomRepository.findByCodeIgnoreCase(code).isEmpty()) {
                return code;
            }
        }
        throw new ConflictException("Could not generate a unique classroom code");
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder(6);
        for (int index = 0; index < 6; index++) {
            code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }
}
