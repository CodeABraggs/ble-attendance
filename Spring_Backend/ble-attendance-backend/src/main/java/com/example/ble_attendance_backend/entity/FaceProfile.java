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

/** A student's enrolled face: the reference embedding attendance selfies are compared against. */
@Entity
@Table(name = "face_profiles")
public class FaceProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // 192 little-endian float32 values from MobileFaceNet, L2-normalized.
    @Column(nullable = false, length = 768)
    private byte[] embedding;

    // JPEG thumbnail shown to teachers next to attendance selfies.
    @Column(nullable = false, length = 200_000)
    private byte[] referencePhoto;

    @Column(nullable = false)
    private Instant enrolledAt;

    protected FaceProfile() {
    }

    public FaceProfile(User user, byte[] embedding, byte[] referencePhoto) {
        this.user = user;
        this.embedding = embedding;
        this.referencePhoto = referencePhoto;
        this.enrolledAt = Instant.now();
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public byte[] getEmbedding() { return embedding; }
    public byte[] getReferencePhoto() { return referencePhoto; }
    public Instant getEnrolledAt() { return enrolledAt; }
}
