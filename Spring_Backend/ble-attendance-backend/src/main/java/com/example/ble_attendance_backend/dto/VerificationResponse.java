package com.example.ble_attendance_backend.dto;

/** Photos are base64 JPEG; referencePhoto is null if the student's face enrollment was reset. */
public record VerificationResponse(String referencePhoto, String attendancePhoto, float similarity,
                                   float spoofScore, String reviewReason) {
}
