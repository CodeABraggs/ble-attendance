package com.example.ble_attendance_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(nullable = false, length = 120)
    private String password;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // Base64 X.509 public key of the phone's hardware-backed signing key; one phone per account.
    @Column(unique = true, length = 512)
    private String devicePublicKey;

    protected User() {
    }

    public User(String email, String password, Role role) {
        this.email = email;
        this.password = password;
        this.name = email.substring(0, email.indexOf('@'));
        this.role = role;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public Role getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
    public String getDevicePublicKey() { return devicePublicKey; }
    public void setDevicePublicKey(String devicePublicKey) { this.devicePublicKey = devicePublicKey; }
}