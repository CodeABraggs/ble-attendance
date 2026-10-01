package com.example.ble_attendance_backend.repository;

import com.example.ble_attendance_backend.entity.AttendanceChallenge;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceChallengeRepository extends JpaRepository<AttendanceChallenge, String> {
    @Modifying
    @Query("delete from AttendanceChallenge c where c.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);
}
