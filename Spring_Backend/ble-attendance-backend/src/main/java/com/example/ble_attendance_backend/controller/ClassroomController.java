package com.example.ble_attendance_backend.controller;

import com.example.ble_attendance_backend.dto.ClassroomResponse;
import com.example.ble_attendance_backend.dto.CreateClassroomRequest;
import com.example.ble_attendance_backend.dto.JoinClassroomRequest;
import com.example.ble_attendance_backend.security.AuthenticatedUser;
import com.example.ble_attendance_backend.service.ClassroomService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/classrooms")
public class ClassroomController {
    private final ClassroomService classroomService;

    public ClassroomController(ClassroomService classroomService) {
        this.classroomService = classroomService;
    }

    /** Classrooms the caller teaches (teacher) or has joined (student). */
    @GetMapping
    public List<ClassroomResponse> getMyClassrooms(AuthenticatedUser caller) {
        return classroomService.getMyClassrooms(caller);
    }

    @GetMapping("/{classroomId}")
    public ClassroomResponse getClassroom(AuthenticatedUser caller, @PathVariable Long classroomId) {
        return classroomService.getClassroom(caller, classroomId);
    }

    @GetMapping("/code/{code}")
    public ClassroomResponse findByCode(AuthenticatedUser caller, @PathVariable String code) {
        return classroomService.findByCode(caller, code);
    }

    @PostMapping
    public ResponseEntity<ClassroomResponse> create(AuthenticatedUser caller, @Valid @RequestBody CreateClassroomRequest request) {
        return ResponseEntity.status(201).body(classroomService.createClassroom(caller, request.name()));
    }

    @PostMapping("/join")
    public ClassroomResponse join(AuthenticatedUser caller, @Valid @RequestBody JoinClassroomRequest request) {
        return classroomService.joinClassroom(caller, request.code());
    }
}
