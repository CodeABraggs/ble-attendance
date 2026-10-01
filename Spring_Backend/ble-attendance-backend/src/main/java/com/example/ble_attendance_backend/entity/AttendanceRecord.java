package com.example.ble_attendance_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "attendance_records", uniqueConstraints = @UniqueConstraint(name = "uk_session_student", columnNames = {"session_id", "student_id"}))
public class AttendanceRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private AttendanceSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(nullable = false)
    private Instant attendanceTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceStatus status;

    protected AttendanceRecord() {
    }

    public AttendanceRecord(AttendanceSession session, User student) {
        this.session = session;
        this.student = student;
        this.attendanceTime = Instant.now();
        this.status = AttendanceStatus.ABSENT;
    }

    public Long getId() { return id; }
    public AttendanceSession getSession() { return session; }
    public User getStudent() { return student; }
    public Instant getAttendanceTime() { return attendanceTime; }
    public AttendanceStatus getStatus() { return status; }
    public void setStatus(AttendanceStatus status) {
        this.status = status;
        this.attendanceTime = Instant.now();
    }
}