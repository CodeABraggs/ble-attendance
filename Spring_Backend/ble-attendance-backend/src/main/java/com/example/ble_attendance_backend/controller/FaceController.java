package com.example.ble_attendance_backend.controller;

import com.example.ble_attendance_backend.dto.BindDeviceRequest;
import com.example.ble_attendance_backend.dto.EnrollFaceRequest;
import com.example.ble_attendance_backend.security.AuthenticatedUser;
import com.example.ble_attendance_backend.service.FaceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class FaceController {
    private final FaceService faceService;

    public FaceController(FaceService faceService) {
        this.faceService = faceService;
    }

    /** Links the caller's account to this phone's hardware-backed signing key. */
    @PutMapping("/devices/me")
    public ResponseEntity<Void> bindDevice(AuthenticatedUser caller, @Valid @RequestBody BindDeviceRequest request) {
        faceService.bindDevice(caller, request.publicKey());
        return ResponseEntity.noContent().build();
    }

    /** One-time face enrollment; a teacher can reset it from a classroom the student belongs to. */
    @PostMapping("/face/enroll")
    public ResponseEntity<Void> enroll(AuthenticatedUser caller, @Valid @RequestBody EnrollFaceRequest request) {
        faceService.enroll(caller, request);
        return ResponseEntity.status(201).build();
    }
}
