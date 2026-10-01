package com.example.ble_attendance_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "classroom_members", uniqueConstraints = @UniqueConstraint(name = "uk_classroom_student", columnNames = {"classroom_id", "student_id"}))
public class ClassroomMembership {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "classroom_id", nullable = false)
    private Classroom classroom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(nullable = false, updatable = false)
    private Instant joinedAt;

    protected ClassroomMembership() {
    }

    public ClassroomMembership(Classroom classroom, User student) {
        this.classroom = classroom;
        this.student = student;
        this.joinedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Classroom getClassroom() { return classroom; }
    public User getStudent() { return student; }
    public Instant getJoinedAt() { return joinedAt; }
}