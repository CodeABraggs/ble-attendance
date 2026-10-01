package com.example.ble_attendance_backend.dto;

import jakarta.validation.constraints.NotNull;

/** beaconCode is the rotating code the student's phone read from the teacher's BLE beacon. */
public record ChallengeRequest(@NotNull(message = "beaconCode is required") Integer beaconCode) {
}
