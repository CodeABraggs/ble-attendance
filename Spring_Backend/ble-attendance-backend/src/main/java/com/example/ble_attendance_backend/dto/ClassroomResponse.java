package com.example.ble_attendance_backend.dto;

import com.example.ble_attendance_backend.entity.Classroom;
import com.example.ble_attendance_backend.repository.ClassroomMembershipRepository;
import java.time.Instant;

public record ClassroomResponse(Long classId, String name, String code, Long teacherId, long memberCount, Instant createdAt) {
    public static ClassroomResponse from(Classroom classroom, ClassroomMembershipRepository membershipRepository) {
        return new ClassroomResponse(
                classroom.getId(),
                classroom.getName(),
                classroom.getCode(),
                classroom.getTeacher().getId(),
                membershipRepository.countByClassroom(classroom),
                classroom.getCreatedAt());
    }
}