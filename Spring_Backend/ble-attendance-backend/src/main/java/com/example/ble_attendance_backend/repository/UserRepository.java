package com.example.ble_attendance_backend.repository;

import com.example.ble_attendance_backend.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByDevicePublicKey(String devicePublicKey);
}