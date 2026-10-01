package com.example.ble_attendance_backend.repository;

import com.example.ble_attendance_backend.entity.Classroom;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassroomRepository extends JpaRepository<Classroom, Long> {
    Optional<Classroom> findByCodeIgnoreCase(String code);
    List<Classroom> findByTeacherIdOrderByCreatedAtDesc(Long teacherId);
}