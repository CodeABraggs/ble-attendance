package com.example.ble_attendance_backend.service;

import com.example.ble_attendance_backend.dto.LoginRequest;
import com.example.ble_attendance_backend.dto.LoginResponse;
import com.example.ble_attendance_backend.dto.MeResponse;
import com.example.ble_attendance_backend.dto.RegisterRequest;
import com.example.ble_attendance_backend.entity.User;
import com.example.ble_attendance_backend.exception.BadRequestException;
import com.example.ble_attendance_backend.exception.ConflictException;
import com.example.ble_attendance_backend.exception.ResourceNotFoundException;
import com.example.ble_attendance_backend.exception.UnauthorizedException;
import com.example.ble_attendance_backend.repository.FaceProfileRepository;
import com.example.ble_attendance_backend.repository.UserRepository;
import com.example.ble_attendance_backend.security.AuthTokenService;
import com.example.ble_attendance_backend.security.AuthenticatedUser;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService tokenService;
    private final FaceProfileRepository faceProfileRepository;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuthTokenService tokenService,
                       FaceProfileRepository faceProfileRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.faceProfileRepository = faceProfileRepository;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        // Same message for unknown email and wrong password so accounts can't be enumerated.
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPassword()))
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        if (request.role() != null && request.role() != user.getRole()) {
            throw new BadRequestException("This account is registered as a " + user.getRole() + ", not a " + request.role());
        }
        return LoginResponse.from(user, tokenService.issue(user), isFaceEnrolled(user.getId()));
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new ConflictException("An account with that email already exists");
        }
        User user = userRepository.save(new User(email, passwordEncoder.encode(request.password()), request.role()));
        return LoginResponse.from(user, tokenService.issue(user), false);
    }

    @Transactional(readOnly = true)
    public MeResponse me(AuthenticatedUser caller) {
        User user = requireUser(caller.id());
        return new MeResponse(user.getId(), user.getEmail(), user.getRole(),
                isFaceEnrolled(user.getId()), user.getDevicePublicKey() != null);
    }

    // A profile from an older face pipeline doesn't count; the app then asks the student to enroll again.
    private boolean isFaceEnrolled(Long userId) {
        return faceProfileRepository.existsByUserIdAndModelVersion(userId, FaceService.MODEL_VERSION);
    }

    @Transactional(readOnly = true)
    public User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }
}
