package com.example.ble_attendance_backend.service;

import com.example.ble_attendance_backend.dto.EnrollFaceRequest;
import com.example.ble_attendance_backend.dto.FaceSampleFields;
import com.example.ble_attendance_backend.entity.Classroom;
import com.example.ble_attendance_backend.entity.FaceProfile;
import com.example.ble_attendance_backend.entity.Role;
import com.example.ble_attendance_backend.entity.User;
import com.example.ble_attendance_backend.exception.BadRequestException;
import com.example.ble_attendance_backend.exception.ConflictException;
import com.example.ble_attendance_backend.exception.ForbiddenException;
import com.example.ble_attendance_backend.exception.ResourceNotFoundException;
import com.example.ble_attendance_backend.exception.UnauthorizedException;
import com.example.ble_attendance_backend.repository.ClassroomMembershipRepository;
import com.example.ble_attendance_backend.repository.ClassroomRepository;
import com.example.ble_attendance_backend.repository.FaceProfileRepository;
import com.example.ble_attendance_backend.repository.UserRepository;
import com.example.ble_attendance_backend.security.AuthenticatedUser;
import com.example.ble_attendance_backend.security.DeviceSignatures;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.GeneralSecurityException;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Face verification decisions. The phone runs the face models (detection, liveness, MobileFaceNet);
 * the server only compares the submitted 192-value embedding with the enrolled one, which is cheap.
 */
@Service
public class FaceService {
    public static final int EMBEDDING_SIZE = 192;
    private static final int MAX_PHOTO_BYTES = 150_000;

    public enum Decision { ACCEPT, REVIEW, REJECT }

    public record Evaluation(Decision decision, float similarity, float spoofScore, String reason) {
    }

    /** A validated face sample from a request. */
    public record FaceSample(float[] embedding, byte[] embeddingBytes, byte[] photo, int spoofScoreBp) {
        public float spoofScore() {
            return spoofScoreBp / 10_000f;
        }
    }

    private final UserRepository userRepository;
    private final FaceProfileRepository faceProfileRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomMembershipRepository membershipRepository;
    private final float matchThreshold;
    private final float reviewThreshold;
    private final float spoofThreshold;
    private final float replayThreshold;

    public FaceService(UserRepository userRepository, FaceProfileRepository faceProfileRepository,
                       ClassroomRepository classroomRepository, ClassroomMembershipRepository membershipRepository,
                       @Value("${attendance.face.match-threshold:0.60}") float matchThreshold,
                       @Value("${attendance.face.review-threshold:0.45}") float reviewThreshold,
                       @Value("${attendance.face.spoof-threshold:0.20}") float spoofThreshold,
                       @Value("${attendance.face.replay-threshold:0.995}") float replayThreshold) {
        this.userRepository = userRepository;
        this.faceProfileRepository = faceProfileRepository;
        this.classroomRepository = classroomRepository;
        this.membershipRepository = membershipRepository;
        this.matchThreshold = matchThreshold;
        this.reviewThreshold = reviewThreshold;
        this.spoofThreshold = spoofThreshold;
        this.replayThreshold = replayThreshold;
    }

    /** Links the caller's account to the phone holding this key. A phone can belong to only one account. */
    @Transactional
    public void bindDevice(AuthenticatedUser caller, String publicKey) {
        try {
            DeviceSignatures.parsePublicKey(publicKey);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new BadRequestException("publicKey is not a valid EC public key");
        }
        userRepository.findByDevicePublicKey(publicKey)
                .filter(owner -> !owner.getId().equals(caller.id()))
                .ifPresent(owner -> {
                    throw new ConflictException("This phone is already linked to another account");
                });
        User user = requireUser(caller.id());
        user.setDevicePublicKey(publicKey);
    }

    @Transactional(readOnly = true)
    public boolean isEnrolled(Long userId) {
        return faceProfileRepository.existsByUserId(userId);
    }

    @Transactional
    public void enroll(AuthenticatedUser caller, EnrollFaceRequest request) {
        if (caller.role() != Role.STUDENT) {
            throw new ForbiddenException("Only a STUDENT enrolls a face");
        }
        if (faceProfileRepository.existsByUserId(caller.id())) {
            throw new ConflictException("Face is already enrolled. Ask your teacher to reset it");
        }
        User user = requireUser(caller.id());
        FaceSample sample = parseSample(request);
        requireDeviceSignature(user, "ENROLL|" + user.getId() + "|" + sampleDigest(sample), request.signature());
        if (sample.spoofScore() > spoofThreshold) {
            throw new BadRequestException("Liveness check failed. Use your real face in good light and try again");
        }
        faceProfileRepository.save(new FaceProfile(user, sample.embeddingBytes(), sample.photo()));
    }

    /** Compares a sample with the student's enrolled face. */
    @Transactional(readOnly = true)
    public Evaluation evaluate(Long studentId, FaceSample sample) {
        FaceProfile profile = faceProfileRepository.findByUserId(studentId)
                .orElseThrow(() -> new BadRequestException("Set up face verification before marking attendance"));
        float similarity = cosine(toFloats(profile.getEmbedding()), sample.embedding());
        float spoofScore = sample.spoofScore();
        if (spoofScore > spoofThreshold) {
            return new Evaluation(Decision.REJECT, similarity, spoofScore,
                    "Liveness check failed. Use your real face in good light and try again");
        }
        if (similarity >= replayThreshold) {
            // A live capture never reproduces the enrolled embedding exactly.
            return new Evaluation(Decision.REVIEW, similarity, spoofScore, "Face sample identical to enrollment (possible replay)");
        }
        if (similarity >= matchThreshold) {
            return new Evaluation(Decision.ACCEPT, similarity, spoofScore, null);
        }
        if (similarity >= reviewThreshold) {
            return new Evaluation(Decision.REVIEW, similarity, spoofScore,
                    String.format("Borderline face match (similarity %.2f)", similarity));
        }
        return new Evaluation(Decision.REJECT, similarity, spoofScore, "Face does not match the enrolled student");
    }

    /** Lets a student's teacher clear a bad enrollment so the student can enroll again. */
    @Transactional
    public void resetEnrollment(AuthenticatedUser caller, Long classId, Long studentId) {
        Classroom classroom = classroomRepository.findById(classId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found: " + classId));
        if (caller.role() != Role.TEACHER || !classroom.getTeacher().getId().equals(caller.id())) {
            throw new ForbiddenException("Only the classroom's teacher can do this");
        }
        if (!membershipRepository.existsByClassroomIdAndStudentId(classId, studentId)) {
            throw new ResourceNotFoundException("Student is not a member of this classroom");
        }
        faceProfileRepository.deleteByUserId(studentId);
    }

    @Transactional(readOnly = true)
    public byte[] referencePhoto(Long studentId) {
        return faceProfileRepository.findByUserId(studentId).map(FaceProfile::getReferencePhoto).orElse(null);
    }

    public void requireDeviceSignature(User user, String payload, String signature) {
        if (user.getDevicePublicKey() == null) {
            throw new BadRequestException("This phone is not linked to your account. Log in again");
        }
        if (!DeviceSignatures.verify(user.getDevicePublicKey(), payload, signature)) {
            throw new UnauthorizedException("Request was not signed by your linked phone");
        }
    }

    /** The part of a signed payload that covers the face sample. */
    public static String sampleDigest(FaceSample sample) {
        return DeviceSignatures.sha256Hex(sample.embeddingBytes()) + "|" + DeviceSignatures.sha256Hex(sample.photo())
                + "|" + sample.spoofScoreBp();
    }

    public static FaceSample parseSample(FaceSampleFields fields) {
        byte[] embeddingBytes = decodeBase64(fields.embedding(), "embedding");
        if (embeddingBytes.length != EMBEDDING_SIZE * Float.BYTES) {
            throw new BadRequestException("embedding must contain " + EMBEDDING_SIZE + " values");
        }
        float[] embedding = toFloats(embeddingBytes);
        double norm = 0;
        for (float value : embedding) {
            if (!Float.isFinite(value)) {
                throw new BadRequestException("embedding contains invalid values");
            }
            norm += value * value;
        }
        if (norm < 1e-6) {
            throw new BadRequestException("embedding is empty");
        }
        byte[] photo = decodeBase64(fields.photo(), "photo");
        if (photo.length > MAX_PHOTO_BYTES || photo.length < 3
                || (photo[0] & 0xFF) != 0xFF || (photo[1] & 0xFF) != 0xD8 || (photo[2] & 0xFF) != 0xFF) {
            throw new BadRequestException("photo must be a JPEG under " + MAX_PHOTO_BYTES / 1000 + " KB");
        }
        return new FaceSample(embedding, embeddingBytes, photo, fields.spoofScoreBp());
    }

    static float cosine(float[] a, float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int index = 0; index < a.length; index++) {
            dot += a[index] * b[index];
            normA += a[index] * a[index];
            normB += b[index] * b[index];
        }
        return (float) (dot / Math.sqrt(normA * normB));
    }

    static float[] toFloats(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] values = new float[bytes.length / Float.BYTES];
        for (int index = 0; index < values.length; index++) {
            values[index] = buffer.getFloat();
        }
        return values;
    }

    private static byte[] decodeBase64(String value, String field) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(field + " must be base64");
        }
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }
}
