package com.example.ble_attendance_backend.controller;

import com.example.ble_attendance_backend.dto.LoginRequest;
import com.example.ble_attendance_backend.dto.LoginResponse;
import com.example.ble_attendance_backend.dto.RegisterRequest;
import com.example.ble_attendance_backend.security.AuthInterceptor;
import com.example.ble_attendance_backend.security.AuthTokenService;
import com.example.ble_attendance_backend.security.AuthenticatedUser;
import com.example.ble_attendance_backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;
    private final AuthTokenService tokenService;

    public AuthController(UserService userService, AuthTokenService tokenService) {
        this.userService = userService;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }

    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(201).body(userService.register(request));
    }

    @GetMapping("/me")
    public AuthenticatedUser me(AuthenticatedUser caller) {
        return caller;
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestAttribute(AuthInterceptor.AUTH_TOKEN_ATTRIBUTE) String token) {
        tokenService.revoke(token);
        return ResponseEntity.noContent().build();
    }
}
