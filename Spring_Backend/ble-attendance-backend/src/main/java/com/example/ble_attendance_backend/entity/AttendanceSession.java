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
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

@Entity
@Table(name = "attendance_sessions")
public class AttendanceSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "classroom_id", nullable = false)
    private Classroom classroom;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    @Column(nullable = false, length = 64)
    private String zoneId;

    // Hex HMAC key the teacher's phone uses to derive rotating beacon codes.
    @Column(nullable = false, length = 64)
    private String beaconSecret;

    protected AttendanceSession() {
    }

    public AttendanceSession(Classroom classroom, LocalDate date, LocalTime startTime, LocalTime endTime,
                             ZoneId zone, String beaconSecret) {
        this.classroom = classroom;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = SessionStatus.ACTIVE;
        this.zoneId = zone.getId();
        this.beaconSecret = beaconSecret;
    }

    public Long getId() { return id; }
    public Classroom getClassroom() { return classroom; }
    public LocalDate getDate() { return date; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public SessionStatus getStatus() { return status; }
    public String getBeaconSecret() { return beaconSecret; }

    public ZoneId getZone() {
        try {
            return zoneId == null || zoneId.isBlank() ? ZoneId.systemDefault() : ZoneId.of(zoneId);
        } catch (DateTimeException exception) {
            return ZoneId.systemDefault();
        }
    }

    public Instant getStartInstant() {
        return LocalDateTime.of(date, startTime).atZone(getZone()).toInstant();
    }

    public Instant getEndInstant() {
        return LocalDateTime.of(date, endTime).atZone(getZone()).toInstant();
    }
}