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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Face verification decisions. The phone runs the face models (detection, alignment, anti-spoofing,
 * MobileFaceNet); the server only compares the submitted 192-value embedding with the student's
 * templates, which is cheap.
 */
@Service
public class FaceService {
    /**
     * Version of the app's face pipeline (FaceModels.MODEL_VERSION). Embeddings from different versions
     * aren't comparable, so samples must match this and older enrollments must be redone.
     */
    public static final int MODEL_VERSION = 2;
    public static final int EMBEDDING_SIZE = 192;
    private static final int EMBEDDING_BYTES = EMBEDDING_SIZE * Float.BYTES;
    private static final int MAX_PHOTO_BYTES = 150_000;
    // Recent confident matches kept as extra templates, besides the enrolled one.
    private static final int MAX_RECENT_TEMPLATES = 3;
    private static final Logger log = LoggerFactory.getLogger(FaceService.class);

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
    private final float adaptThreshold;

    public FaceService(UserRepository userRepository, FaceProfileRepository faceProfileRepository,
                       ClassroomRepository classroomRepository, ClassroomMembershipRepository membershipRepository,
                       @Value("${attendance.face.match-threshold:0.50}") float matchThreshold,
                       @Value("${attendance.face.review-threshold:0.35}") float reviewThreshold,
                       @Value("${attendance.face.spoof-threshold:0.20}") float spoofThreshold,
                       @Value("${attendance.face.replay-threshold:0.995}") float replayThreshold,
                       @Value("${attendance.face.adapt-threshold:0.65}") float adaptThreshold) {
        this.userRepository = userRepository;
        this.faceProfileRepository = faceProfileRepository;
        this.classroomRepository = classroomRepository;
        this.membershipRepository = membershipRepository;
        this.matchThreshold = matchThreshold;
        this.reviewThreshold = reviewThreshold;
        this.spoofThreshold = spoofThreshold;
        this.replayThreshold = replayThreshold;
        this.adaptThreshold = adaptThreshold;
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

    /** True only for an enrollment made with the current face pipeline. */
    @Transactional(readOnly = true)
    public boolean isEnrolled(Long userId) {
        return faceProfileRepository.existsByUserIdAndModelVersion(userId, MODEL_VERSION);
    }

    /**
     * One-time enrollment. A profile from an older face pipeline can't be compared any more, so the student
     * may replace it themselves; a current one can only be reset by a teacher.
     */
    @Transactional
    public void enroll(AuthenticatedUser caller, EnrollFaceRequest request) {
        if (caller.role() != Role.STUDENT) {
            throw new ForbiddenException("Only a STUDENT enrolls a face");
        }
        Optional<FaceProfile> existing = faceProfileRepository.findByUserId(caller.id());
        if (existing.filter(FaceService::isCurrent).isPresent()) {
            throw new ConflictException("Face is already enrolled. Ask your teacher to reset it");
        }
        User user = requireUser(caller.id());
        FaceSample sample = parseSample(request);
        requireDeviceSignature(user, "ENROLL|" + user.getId() + "|" + sampleDigest(sample), request.signature());
        if (sample.spoofScore() > spoofThreshold) {
            log.info("Face enrollment rejected for user {}: spoof score {}", user.getId(), format(sample.spoofScore()));
            throw new BadRequestException("Liveness check failed. Use your real face in good light and try again");
        }
        if (existing.isPresent()) {
            existing.get().reenroll(sample.embeddingBytes(), sample.photo(), MODEL_VERSION);
        } else {
            faceProfileRepository.save(new FaceProfile(user, sample.embeddingBytes(), sample.photo(), MODEL_VERSION));
        }
        log.info("Face enrolled for user {} (spoof score {})", user.getId(), format(sample.spoofScore()));
    }

    /** Compares a sample with the student's enrolled face and recent confident matches; the best one counts. */
    // Not read-only: the profile it loads may then be updated by rememberConfidentSample in the same transaction.
    @Transactional
    public Evaluation evaluate(Long studentId, FaceSample sample) {
        FaceProfile profile = faceProfileRepository.findByUserId(studentId)
                .filter(FaceService::isCurrent)
                .orElseThrow(() -> new BadRequestException("Set up face verification before marking attendance"));
        float similarity = -1f;
        boolean replay = false;
        for (float[] template : templates(profile)) {
            float templateSimilarity = cosine(template, sample.embedding());
            similarity = Math.max(similarity, templateSimilarity);
            // A live capture never reproduces a stored embedding exactly.
            replay |= templateSimilarity >= replayThreshold;
        }
        Evaluation evaluation = decide(similarity, sample.spoofScore(), replay);
        log.info("Face check for student {}: similarity {}, spoof score {} -> {}",
                studentId, format(similarity), format(sample.spoofScore()), evaluation.decision());
        return evaluation;
    }

    /**
     * Remembers a confidently matched sample as an extra template, so later checks also compare against how
     * the student looks in their usual classroom conditions. Only clear matches are kept, so this can't
     * drift towards someone else's face.
     */
    @Transactional
    public void rememberConfidentSample(Long studentId, FaceSample sample, float similarity) {
        if (similarity < adaptThreshold) {
            return;
        }
        faceProfileRepository.findByUserId(studentId)
                .filter(FaceService::isCurrent)
                .ifPresent(profile -> profile.addRecentEmbedding(sample.embeddingBytes(), MAX_RECENT_TEMPLATES));
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
        if (!Objects.equals(fields.modelVersion(), MODEL_VERSION)) {
            throw new BadRequestException("This app version is out of date. Update the app and try again");
        }
        byte[] embeddingBytes = decodeBase64(fields.embedding(), "embedding");
        if (embeddingBytes.length != EMBEDDING_BYTES) {
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

    private Evaluation decide(float similarity, float spoofScore, boolean replay) {
        if (spoofScore > spoofThreshold) {
            return new Evaluation(Decision.REJECT, similarity, spoofScore,
                    "Make sure it's your real face in good light, not a photo or screen");
        }
        if (replay) {
            return new Evaluation(Decision.REVIEW, similarity, spoofScore, "Face sample identical to a stored one (possible replay)");
        }
        if (similarity >= matchThreshold) {
            return new Evaluation(Decision.ACCEPT, similarity, spoofScore, null);
        }
        if (similarity >= reviewThreshold) {
            return new Evaluation(Decision.REVIEW, similarity, spoofScore,
                    String.format("Borderline face match (similarity %.2f)", similarity));
        }
        return new Evaluation(Decision.REJECT, similarity, spoofScore,
                "Your face didn't match clearly. Look straight at the camera in good light");
    }

    private static boolean isCurrent(FaceProfile profile) {
        return Objects.equals(profile.getModelVersion(), MODEL_VERSION);
    }

    private static List<float[]> templates(FaceProfile profile) {
        List<float[]> templates = new ArrayList<>();
        templates.add(toFloats(profile.getEmbedding()));
        byte[] recent = profile.getRecentEmbeddings();
        if (recent != null) {
            for (int offset = 0; offset + EMBEDDING_BYTES <= recent.length; offset += EMBEDDING_BYTES) {
                byte[] embedding = new byte[EMBEDDING_BYTES];
                System.arraycopy(recent, offset, embedding, 0, EMBEDDING_BYTES);
                templates.add(toFloats(embedding));
            }
        }
        return templates;
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

    private static String format(float value) {
        return String.format("%.3f", value);
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
