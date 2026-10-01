package com.example.ble_attendance_backend.service;

import com.example.ble_attendance_backend.dto.ClassroomResponse;
import com.example.ble_attendance_backend.dto.CreateClassroomRequest;
import com.example.ble_attendance_backend.entity.Classroom;
import com.example.ble_attendance_backend.entity.ClassroomMembership;
import com.example.ble_attendance_backend.entity.Role;
import com.example.ble_attendance_backend.entity.User;
import com.example.ble_attendance_backend.exception.BadRequestException;
import com.example.ble_attendance_backend.exception.ConflictException;
import com.example.ble_attendance_backend.exception.ResourceNotFoundException;
import com.example.ble_attendance_backend.repository.ClassroomMembershipRepository;
import com.example.ble_attendance_backend.repository.ClassroomRepository;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassroomService {
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();
    private final ClassroomRepository classroomRepository;
    private final ClassroomMembershipRepository membershipRepository;
    private final UserService userService;

    public ClassroomService(ClassroomRepository classroomRepository,
                            ClassroomMembershipRepository membershipRepository,
                            UserService userService) {
        this.classroomRepository = classroomRepository;
        this.membershipRepository = membershipRepository;
        this.userService = userService;
    }

    @Transactional
    public ClassroomResponse createClassroom(Long teacherId, CreateClassroomRequest request) {
        User teacher = userService.requireUser(teacherId);
        requireRole(teacher, Role.TEACHER);
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                Classroom classroom = classroomRepository.saveAndFlush(
                        new Classroom(request.name().trim(), generateCode(), teacher));
                return ClassroomResponse.from(classroom, membershipRepository);
            } catch (DataIntegrityViolationException exception) {
                if (attempt == 4) {
                    throw new ConflictException("Could not generate a unique classroom code");
                }
            }
        }
        throw new ConflictException("Could not generate a classroom code");
    }

    @Transactional(readOnly = true)
    public List<ClassroomResponse> getTeacherClassrooms(Long teacherId) {
        User teacher = userService.requireUser(teacherId);
        requireRole(teacher, Role.TEACHER);
        return classroomRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId).stream()
                .map(classroom -> ClassroomResponse.from(classroom, membershipRepository))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ClassroomResponse getClassroom(Long classroomId) {
        return ClassroomResponse.from(requireClassroom(classroomId), membershipRepository);
    }

    @Transactional(readOnly = true)
    public ClassroomResponse findByCode(String code) {
        String normalizedCode = normalizeCode(code);
        return classroomRepository.findByCodeIgnoreCase(normalizedCode)
                .map(classroom -> ClassroomResponse.from(classroom, membershipRepository))
                .orElseThrow(() -> new ResourceNotFoundException("No classroom found for code: " + normalizedCode));
    }

    @Transactional
    public ClassroomResponse joinClassroom(Long studentId, String code) {
        User student = userService.requireUser(studentId);
        requireRole(student, Role.STUDENT);
        Classroom classroom = classroomRepository.findByCodeIgnoreCase(normalizeCode(code))
                .orElseThrow(() -> new ResourceNotFoundException("No classroom found for that code"));
        if (membershipRepository.existsByClassroomIdAndStudentId(classroom.getId(), studentId)) {
            throw new ConflictException("Student is already a member of this classroom");
        }
        membershipRepository.save(new ClassroomMembership(classroom, student));
        return ClassroomResponse.from(classroom, membershipRepository);
    }

    @Transactional(readOnly = true)
    public List<ClassroomResponse> getStudentClassrooms(Long studentId) {
        User student = userService.requireUser(studentId);
        requireRole(student, Role.STUDENT);
        return membershipRepository.findByStudentIdOrderByJoinedAtDesc(studentId).stream()
                .map(ClassroomMembership::getClassroom)
                .map(classroom -> ClassroomResponse.from(classroom, membershipRepository))
                .collect(Collectors.toList());
    }

    private Classroom requireClassroom(Long classroomId) {
        return classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found: " + classroomId));
    }

    private void requireRole(User user, Role role) {
        if (user.getRole() != role) {
            throw new BadRequestException("User must have role " + role);
        }
    }

    private String normalizeCode(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9]{6}")) {
            throw new BadRequestException("Classroom code must be 6 letters or digits");
        }
        return normalized;
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder(6);
        for (int index = 0; index < 6; index++) {
            code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }
}