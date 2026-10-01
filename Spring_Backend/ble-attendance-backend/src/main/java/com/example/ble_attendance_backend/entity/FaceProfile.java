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

    // Version of the phone's face pipeline that produced the embeddings; null for pre-versioning profiles.
    // Embeddings from different versions can't be compared, so an outdated profile must be re-enrolled.
    private Integer modelVersion;

    // Embeddings from recent confidently-matched attendance checks, newest first, concatenated.
    // Matching against these as well adapts to the student's usual lighting and appearance.
    @Column(length = 2304)
    private byte[] recentEmbeddings;

    protected FaceProfile() {
    }

    public FaceProfile(User user, byte[] embedding, byte[] referencePhoto, int modelVersion) {
        this.user = user;
        this.embedding = embedding;
        this.referencePhoto = referencePhoto;
        this.enrolledAt = Instant.now();
        this.modelVersion = modelVersion;
    }

    /** Replaces an outdated enrollment in place (the user_id column is unique, so no delete + insert). */
    public void reenroll(byte[] embedding, byte[] referencePhoto, int modelVersion) {
        this.embedding = embedding;
        this.referencePhoto = referencePhoto;
        this.enrolledAt = Instant.now();
        this.modelVersion = modelVersion;
        this.recentEmbeddings = null;
    }

    /** Keeps [newEmbedding] plus the newest earlier ones, at most [maxRecent] in total. */
    public void addRecentEmbedding(byte[] newEmbedding, int maxRecent) {
        int keptOld = recentEmbeddings == null ? 0
                : Math.min(recentEmbeddings.length, (maxRecent - 1) * newEmbedding.length);
        byte[] combined = new byte[newEmbedding.length + keptOld];
        System.arraycopy(newEmbedding, 0, combined, 0, newEmbedding.length);
        if (keptOld > 0) {
            System.arraycopy(recentEmbeddings, 0, combined, newEmbedding.length, keptOld);
        }
        this.recentEmbeddings = combined;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public byte[] getEmbedding() { return embedding; }
    public byte[] getReferencePhoto() { return referencePhoto; }
    public Instant getEnrolledAt() { return enrolledAt; }
    public Integer getModelVersion() { return modelVersion; }
    public byte[] getRecentEmbeddings() { return recentEmbeddings; }
}
