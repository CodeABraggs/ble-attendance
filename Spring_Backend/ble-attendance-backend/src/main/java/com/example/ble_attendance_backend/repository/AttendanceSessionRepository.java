package com.example.ble_attendance_backend.repository;

import com.example.ble_attendance_backend.entity.AttendanceSession;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long> {
    List<AttendanceSession> findByClassroomIdOrderByDateDescStartTimeDesc(Long classroomId);
}
