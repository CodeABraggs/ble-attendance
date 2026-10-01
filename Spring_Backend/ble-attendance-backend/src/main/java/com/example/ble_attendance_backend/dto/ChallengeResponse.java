package com.example.ble_attendance_backend.dto;

import java.util.List;

/** actions: LivenessAction names the student must perform in order before submitting a face sample. */
public record ChallengeResponse(String nonce, List<String> actions, long expiresAtEpochMillis) {
}
