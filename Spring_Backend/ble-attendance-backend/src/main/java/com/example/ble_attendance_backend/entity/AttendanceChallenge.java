package com.example.ble_attendance_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A one-time liveness challenge issued after the student's phone proved it saw the classroom beacon.
 * The student must complete the random actions and submit a face sample before it expires.
 */
@Entity
@Table(name = "attendance_challenges")
public class AttendanceChallenge {
    @Id
    @Column(length = 64)
    private String nonce;

    @Column(nullable = false)
    private Long sessionId;

    @Column(nullable = false)
    private Long studentId;

    // Comma-separated LivenessAction names, in order.
    @Column(nullable = false, length = 100)
    private String actions;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean used;

    // Face samples rejected so far; a few retries are allowed before the beacon must be scanned again.
    @Column(nullable = false)
    private int failedAttempts;

    protected AttendanceChallenge() {
    }

    public AttendanceChallenge(String nonce, Long sessionId, Long studentId, String actions, Instant expiresAt) {
        this.nonce = nonce;
        this.sessionId = sessionId;
        this.studentId = studentId;
        this.actions = actions;
        this.expiresAt = expiresAt;
    }

    public String getNonce() { return nonce; }
    public Long getSessionId() { return sessionId; }
    public Long getStudentId() { return studentId; }
    public String getActions() { return actions; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isUsed() { return used; }
    public void markUsed() { this.used = true; }
    public int getFailedAttempts() { return failedAttempts; }
    public void recordFailedAttempt() { this.failedAttempts++; }
}
