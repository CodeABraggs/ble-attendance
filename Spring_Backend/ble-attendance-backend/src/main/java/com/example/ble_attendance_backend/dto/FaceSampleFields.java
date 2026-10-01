package com.example.ble_attendance_backend.dto;

/**
 * Fields shared by face enrollment and attendance requests.
 * embedding: base64 of 192 little-endian float32 values. photo: base64 JPEG thumbnail.
 * spoofScoreBp: anti-spoofing score x 10000 (lower = more likely a real face).
 * signature: base64 DER ECDSA signature by the bound device key over the request's payload string.
 */
public interface FaceSampleFields {
    String embedding();
    String photo();
    Integer spoofScoreBp();
    String signature();
}
