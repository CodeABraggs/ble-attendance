package com.example.ble_attendance_backend.controller;

import com.example.ble_attendance_backend.dto.ClassroomResponse;
import com.example.ble_attendance_backend.dto.CreateClassroomRequest;
import com.example.ble_attendance_backend.dto.JoinClassroomRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.example.ble_attendance_backend.service.ClassroomService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/classrooms")
public class ClassroomController {
    private final ClassroomService classroomService;

    public ClassroomController(ClassroomService classroomService) {
        this.classroomService = classroomService;
    }

    @GetMapping("/{classroomId}")
    public ClassroomResponse getClassroom(@PathVariable Long classroomId) {
        return classroomService.getClassroom(classroomId);
    }

    @GetMapping("/code/{code}")
    public ClassroomResponse findByCode(@PathVariable String code) {
        return classroomService.findByCode(code);
    }

    @PostMapping
    public ResponseEntity<ClassroomResponse> create(@Valid @RequestBody CreateClassroomRequest request) {
        return ResponseEntity.status(201).body(classroomService.createClassroom(request.teacherId(), request));
    }

    @GetMapping("/teacher/{teacherId}")
    public List<ClassroomResponse> getTeacherClassrooms(@PathVariable Long teacherId) {
        return classroomService.getTeacherClassrooms(teacherId);
    }

    @GetMapping("/student/{studentId}")
    public List<ClassroomResponse> getStudentClassrooms(@PathVariable Long studentId) {
        return classroomService.getStudentClassrooms(studentId);
    }

    @PostMapping("/join")
    public ClassroomResponse join(@Valid @RequestBody JoinClassroomRequest request) {
        return classroomService.joinClassroom(request.studentId(), request.code());
    }
}