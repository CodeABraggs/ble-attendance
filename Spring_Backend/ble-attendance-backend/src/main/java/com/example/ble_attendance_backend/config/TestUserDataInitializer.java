package com.example.ble_attendance_backend.config;

import com.example.ble_attendance_backend.entity.Role;
import com.example.ble_attendance_backend.entity.User;
import com.example.ble_attendance_backend.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TestUserDataInitializer {
    @Bean
    CommandLineRunner seedTestUsers(UserRepository userRepository) {
        return args -> {
            for (int index = 1; index <= 10; index++) {
                createIfMissing(userRepository, "teacher" + index + "@gmail.com", Role.TEACHER);
            }
            for (int index = 1; index <= 100; index++) {
                createIfMissing(userRepository, "student" + index + "@gmail.com", Role.STUDENT);
            }
        };
    }

    private void createIfMissing(UserRepository userRepository, String email, Role role) {
        if (userRepository.findByEmailIgnoreCase(email).isEmpty()) {
            userRepository.save(new User(email, "password123", role));
        }
    }
}