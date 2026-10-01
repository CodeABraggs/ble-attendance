package com.example.ble_attendance_backend.dto;

public record LoginResponse(Long userId, String email, com.example.ble_attendance_backend.entity.Role role) {
	public static LoginResponse from(UserResponse user) {
		return new LoginResponse(user.userId(), user.email(), user.role());
	}
}