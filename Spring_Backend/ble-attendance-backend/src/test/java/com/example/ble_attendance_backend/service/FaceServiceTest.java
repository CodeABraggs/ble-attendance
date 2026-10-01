package com.example.ble_attendance_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.ble_attendance_backend.dto.EnrollFaceRequest;
import com.example.ble_attendance_backend.entity.AttendanceChallenge;
import com.example.ble_attendance_backend.entity.FaceProfile;
import com.example.ble_attendance_backend.entity.Role;
import com.example.ble_attendance_backend.entity.User;
import com.example.ble_attendance_backend.exception.BadRequestException;
import com.example.ble_attendance_backend.repository.ClassroomMembershipRepository;
import com.example.ble_attendance_backend.repository.ClassroomRepository;
import com.example.ble_attendance_backend.repository.FaceProfileRepository;
import com.example.ble_attendance_backend.repository.UserRepository;
import com.example.ble_attendance_backend.security.DeviceSignatures;
import com.example.ble_attendance_backend.service.FaceService.Decision;
import com.example.ble_attendance_backend.service.FaceService.FaceSample;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FaceServiceTest {
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
    private static final long STUDENT_ID = 7L;

    private final FaceProfileRepository profiles = mock(FaceProfileRepository.class);
    private final FaceService service = new FaceService(mock(UserRepository.class), profiles,
            mock(ClassroomRepository.class), mock(ClassroomMembershipRepository.class), 0.50f, 0.35f, 0.20f, 0.995f, 0.65f);

    @Test
    void decisionFollowsSimilarityBands() {
        enrol(unit(0));

        assertEquals(Decision.ACCEPT, service.evaluate(STUDENT_ID, sample(towards(0, 0.70f), 1000)).decision());
        assertEquals(Decision.REVIEW, service.evaluate(STUDENT_ID, sample(towards(0, 0.42f), 1000)).decision());
        assertEquals(Decision.REJECT, service.evaluate(STUDENT_ID, sample(towards(0, 0.20f), 1000)).decision());
    }

    @Test
    void identicalEmbeddingIsFlaggedAsPossibleReplay() {
        enrol(unit(0));
        FaceService.Evaluation evaluation = service.evaluate(STUDENT_ID, sample(unit(0), 1000));
        assertEquals(Decision.REVIEW, evaluation.decision());
        assertTrue(evaluation.reason().contains("replay"));
    }

    @Test
    void spoofedFaceIsRejectedEvenWhenItMatches() {
        enrol(unit(0));
        assertEquals(Decision.REJECT, service.evaluate(STUDENT_ID, sample(towards(0, 0.9f), 2500)).decision());
    }

    @Test
    void confidentMatchesBecomeExtraTemplates() {
        FaceProfile profile = enrol(unit(0));
        // Looks like the enrolled face from axis 1's direction: too far from axis 0 alone.
        float[] usualClassroomLook = towards(0, 0.66f);
        service.rememberConfidentSample(STUDENT_ID, sample(usualClassroomLook, 1000), 0.66f);
        assertEquals(FaceService.EMBEDDING_SIZE * Float.BYTES, profile.getRecentEmbeddings().length);

        // A later sample close to the remembered template but weak against the enrolled one now matches.
        float[] later = mix(usualClassroomLook, unit(2), 0.9f);
        assertEquals(Decision.ACCEPT, service.evaluate(STUDENT_ID, sample(later, 1000)).decision());
    }

    @Test
    void weakMatchesAreNotRemembered() {
        FaceProfile profile = enrol(unit(0));
        service.rememberConfidentSample(STUDENT_ID, sample(towards(0, 0.55f), 1000), 0.55f);
        assertNull(profile.getRecentEmbeddings());
    }

    @Test
    void recentTemplatesAreCappedNewestFirst() {
        FaceProfile profile = enrol(unit(0));
        for (int axis = 1; axis <= 5; axis++) {
            profile.addRecentEmbedding(bytes(unit(axis)), 3);
        }
        byte[] recent = profile.getRecentEmbeddings();
        assertEquals(3 * FaceService.EMBEDDING_SIZE * Float.BYTES, recent.length);
        // Newest (axis 5) first.
        assertEquals(1f, FaceService.toFloats(recent)[5]);
    }

    @Test
    void outdatedEnrollmentDoesNotCount() {
        User student = new User("student@example.com", "hash", Role.STUDENT);
        FaceProfile outdated = new FaceProfile(student, bytes(unit(0)), JPEG, FaceService.MODEL_VERSION - 1);
        when(profiles.findByUserId(STUDENT_ID)).thenReturn(Optional.of(outdated));
        assertThrows(BadRequestException.class, () -> service.evaluate(STUDENT_ID, sample(unit(0), 1000)));
    }

    @Test
    void rejectsSamplesFromAnOlderApp() {
        assertThrows(BadRequestException.class, () -> FaceService.parseSample(
                new EnrollFaceRequest(b64(bytes(unit(0))), b64(JPEG), 0, "sig", FaceService.MODEL_VERSION - 1)));
    }

    @Test
    void rejectsMalformedSamples() {
        assertThrows(BadRequestException.class, () -> FaceService.parseSample(
                new EnrollFaceRequest(Base64.getEncoder().encodeToString(new byte[10]), b64(JPEG), 0, "sig", FaceService.MODEL_VERSION)));
        assertThrows(BadRequestException.class, () -> FaceService.parseSample(
                new EnrollFaceRequest(b64(bytes(unit(0))), b64(new byte[] {1, 2, 3}), 0, "sig", FaceService.MODEL_VERSION)));
    }

    @Test
    void challengeCountsFailedAttempts() {
        AttendanceChallenge challenge = new AttendanceChallenge("nonce", 1L, STUDENT_ID, "", Instant.now().plusSeconds(60));
        challenge.recordFailedAttempt();
        challenge.recordFailedAttempt();
        assertEquals(2, challenge.getFailedAttempts());
        assertFalse(challenge.isUsed());
    }

    @Test
    void verifiesDeviceSignatureOverSampleDigest() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = generator.generateKeyPair();
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

        FaceSample sample = sample(unit(0), 1234);
        String payload = "MARK|5|nonce|" + FaceService.sampleDigest(sample);
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(payload.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(signer.sign());

        assertTrue(DeviceSignatures.verify(publicKey, payload, signature));
        assertFalse(DeviceSignatures.verify(publicKey, payload.replace("|5|", "|6|"), signature));
        assertFalse(DeviceSignatures.verify(publicKey, payload, "bm90LWEtc2lnbmF0dXJl"));
    }

    private FaceProfile enrol(float[] embedding) {
        User student = new User("student@example.com", "hash", Role.STUDENT);
        FaceProfile profile = new FaceProfile(student, bytes(embedding), JPEG, FaceService.MODEL_VERSION);
        when(profiles.findByUserId(STUDENT_ID)).thenReturn(Optional.of(profile));
        return profile;
    }

    private static FaceSample sample(float[] embedding, int spoofScoreBp) {
        return FaceService.parseSample(new EnrollFaceRequest(b64(bytes(embedding)), b64(JPEG), spoofScoreBp, "unused",
                FaceService.MODEL_VERSION));
    }

    /** A unit vector whose cosine with the axis vector is exactly [cosine]. */
    private static float[] towards(int axis, float cosine) {
        float[] result = new float[FaceService.EMBEDDING_SIZE];
        result[axis] = cosine;
        result[axis + 1] = (float) Math.sqrt(1 - cosine * cosine);
        return result;
    }

    /** Normalized blend: [weight] of a plus the rest of b. */
    private static float[] mix(float[] a, float[] b, float weight) {
        float[] result = new float[a.length];
        double norm = 0;
        for (int index = 0; index < a.length; index++) {
            result[index] = weight * a[index] + (1 - weight) * b[index];
            norm += result[index] * result[index];
        }
        for (int index = 0; index < a.length; index++) {
            result[index] /= (float) Math.sqrt(norm);
        }
        return result;
    }

    private static float[] unit(int axis) {
        float[] vector = new float[FaceService.EMBEDDING_SIZE];
        vector[axis] = 1f;
        return vector;
    }

    private static byte[] bytes(float[] values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : values) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    private static String b64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }
}
