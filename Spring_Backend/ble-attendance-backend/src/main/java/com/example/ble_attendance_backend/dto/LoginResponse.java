package com.example.ble_attendance_backend.dto;

import com.example.ble_attendance_backend.entity.Role;
import com.example.ble_attendance_backend.entity.User;
import com.example.ble_attendance_backend.security.AuthTokenService.IssuedToken;

public record LoginResponse(Long userId, String email, Role role, String token, long expiresAtEpochMillis) {
	public static LoginResponse from(User user, IssuedToken token) {
		return new LoginResponse(user.getId(), user.getEmail(), user.getRole(), token.token(), token.expiresAt().toEpochMilli());
	}
}
