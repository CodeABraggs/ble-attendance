package com.example.ble_attendance_backend.exception;

/** The face sample didn't match, but the student may submit another one for the same challenge. */
public class FaceRejectedException extends RuntimeException {
    public FaceRejectedException(String message) {
        super(message);
    }
}
