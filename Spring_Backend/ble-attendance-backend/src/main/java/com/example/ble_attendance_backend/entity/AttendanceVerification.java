package com.example.ble_attendance_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Evidence for a face-verified attendance mark, kept so the teacher can review it. */
@Entity
@Table(name = "attendance_verifications")
public class AttendanceVerification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false, unique = true)
    private AttendanceRecord record;

    @Column(nullable = false)
    private float similarity;

    @Column(nullable = false)
    private float spoofScore;

    @Column(nullable = false, length = 200_000)
    private byte[] photo;

    // Why the mark needs teacher review; null when it was accepted automatically.
    @Column(length = 200)
    private String reviewReason;

    @Column(nullable = false)
    private Instant createdAt;

    protected AttendanceVerification() {
    }

    public AttendanceVerification(AttendanceRecord record) {
        this.record = record;
    }

    public void update(float similarity, float spoofScore, byte[] photo, String reviewReason) {
        this.similarity = similarity;
        this.spoofScore = spoofScore;
        this.photo = photo;
        this.reviewReason = reviewReason;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public AttendanceRecord getRecord() { return record; }
    public float getSimilarity() { return similarity; }
    public float getSpoofScore() { return spoofScore; }
    public byte[] getPhoto() { return photo; }
    public String getReviewReason() { return reviewReason; }
    public Instant getCreatedAt() { return createdAt; }
}
