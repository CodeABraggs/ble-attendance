package com.example.ble_attendance_backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EnrollFaceRequest(
        @NotBlank(message = "embedding is required") String embedding,
        @NotBlank(message = "photo is required") String photo,
        @NotNull(message = "spoofScoreBp is required") @Min(0) @Max(100_000) Integer spoofScoreBp,
        @NotBlank(message = "signature is required") String signature) implements FaceSampleFields {
}
