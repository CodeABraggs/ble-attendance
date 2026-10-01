package com.example.ble_attendance_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BindDeviceRequest(
        @NotBlank(message = "publicKey is required")
        @Size(max = 512, message = "publicKey is too long")
        String publicKey) {
}
