package com.example.ble_attendance_backend.repository;

import com.example.ble_attendance_backend.entity.AttendanceRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {
    List<AttendanceRecord> findBySessionIdOrderByStudentId(Long sessionId);
    Optional<AttendanceRecord> findBySessionIdAndStudentId(Long sessionId, Long studentId);
    boolean existsBySessionIdAndStudentId(Long sessionId, Long studentId);
    List<AttendanceRecord> findBySessionClassroomIdAndStudentIdOrderBySessionDateDescSessionStartTimeDesc(Long classroomId, Long studentId);
}
