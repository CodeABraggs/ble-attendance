package com.example.ble_attendance_backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Completes a liveness challenge issued by the challenge endpoint. */
public record MarkAttendanceRequest(
        @NotBlank(message = "nonce is required") String nonce,
        @NotBlank(message = "embedding is required") String embedding,
        @NotBlank(message = "photo is required") String photo,
        @NotNull(message = "spoofScoreBp is required") @Min(0) @Max(100_000) Integer spoofScoreBp,
        @NotBlank(message = "signature is required") String signature,
        @NotNull(message = "modelVersion is required") Integer modelVersion) implements FaceSampleFields {
}
