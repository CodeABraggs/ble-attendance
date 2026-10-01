package com.example.ble_attendance_backend.service;

import com.example.ble_attendance_backend.dto.LoginResponse;
import com.example.ble_attendance_backend.dto.RegisterRequest;
import com.example.ble_attendance_backend.dto.UserResponse;
import com.example.ble_attendance_backend.dto.LoginRequest;
import com.example.ble_attendance_backend.entity.User;
import com.example.ble_attendance_backend.exception.ResourceNotFoundException;
import com.example.ble_attendance_backend.exception.ConflictException;
import com.example.ble_attendance_backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Account does not exist"));
        if (user.getPassword() == null || !user.getPassword().equals(request.password())) {
            throw new com.example.ble_attendance_backend.exception.BadRequestException("Invalid credentials");
        }
        return LoginResponse.from(UserResponse.from(user));
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new ConflictException("An account with that email already exists");
        }
        return UserResponse.from(userRepository.save(new User(email, request.password(), request.role())));
    }

    @Transactional(readOnly = true)
    public User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }
}