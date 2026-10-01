package com.example.ble_attendance_backend.repository;

import com.example.ble_attendance_backend.entity.Classroom;
import com.example.ble_attendance_backend.entity.ClassroomMembership;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassroomMembershipRepository extends JpaRepository<ClassroomMembership, Long> {
    boolean existsByClassroomIdAndStudentId(Long classroomId, Long studentId);
    Optional<ClassroomMembership> findByClassroomIdAndStudentId(Long classroomId, Long studentId);
    List<ClassroomMembership> findByStudentIdOrderByJoinedAtDesc(Long studentId);
    List<ClassroomMembership> findByClassroomId(Long classroomId);
    long countByClassroom(Classroom classroom);
}