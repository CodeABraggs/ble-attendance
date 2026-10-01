package com.example.ble_attendance_backend.controller;

import com.example.ble_attendance_backend.dto.ClassroomResponse;
import com.example.ble_attendance_backend.dto.CreateClassroomRequest;
import com.example.ble_attendance_backend.dto.JoinClassroomRequest;
import com.example.ble_attendance_backend.security.AuthenticatedUser;
import com.example.ble_attendance_backend.service.ClassroomService;
import com.example.ble_attendance_backend.service.FaceService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final FaceService faceService;

    public ClassroomController(ClassroomService classroomService, FaceService faceService) {
        this.classroomService = classroomService;
        this.faceService = faceService;
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

    /** Clears a student's face enrollment (e.g. a bad photo) so they can enroll again. */
    @DeleteMapping("/{classroomId}/students/{studentId}/face")
    public ResponseEntity<Void> resetStudentFace(AuthenticatedUser caller, @PathVariable Long classroomId, @PathVariable Long studentId) {
        faceService.resetEnrollment(caller, classroomId, studentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/join")
    public ClassroomResponse join(AuthenticatedUser caller, @Valid @RequestBody JoinClassroomRequest request) {
        return classroomService.joinClassroom(caller, request.code());
    }
}
