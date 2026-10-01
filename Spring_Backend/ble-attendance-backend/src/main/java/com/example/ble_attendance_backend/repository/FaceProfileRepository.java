package com.example.ble_attendance_backend.repository;

import com.example.ble_attendance_backend.entity.FaceProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaceProfileRepository extends JpaRepository<FaceProfile, Long> {
    Optional<FaceProfile> findByUserId(Long userId);
    boolean existsByUserId(Long userId);
    void deleteByUserId(Long userId);
}
