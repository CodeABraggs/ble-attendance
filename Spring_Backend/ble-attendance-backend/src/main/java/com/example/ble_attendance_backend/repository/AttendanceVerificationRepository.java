package com.example.ble_attendance_backend.repository;

import com.example.ble_attendance_backend.entity.AttendanceVerification;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceVerificationRepository extends JpaRepository<AttendanceVerification, Long> {
    Optional<AttendanceVerification> findByRecordId(Long recordId);

    @Query("select v.record.id from AttendanceVerification v where v.record.session.id = :sessionId")
    List<Long> findRecordIdsBySessionId(@Param("sessionId") Long sessionId);
}
