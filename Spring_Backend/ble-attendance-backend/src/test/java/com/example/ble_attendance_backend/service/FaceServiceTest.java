package com.example.ble_attendance_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.ble_attendance_backend.dto.EnrollFaceRequest;
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
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FaceServiceTest {
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
    private static final long STUDENT_ID = 7L;

    private final FaceProfileRepository profiles = mock(FaceProfileRepository.class);
    private final FaceService service = new FaceService(mock(UserRepository.class), profiles,
            mock(ClassroomRepository.class), mock(ClassroomMembershipRepository.class), 0.60f, 0.45f, 0.20f, 0.995f);

    @Test
    void decisionFollowsSimilarityBands() {
        float[] enrolled = unit(0);
        enrol(enrolled);

        assertEquals(Decision.ACCEPT, service.evaluate(STUDENT_ID, sample(mix(enrolled, 0.80f), 1000)).decision());
        assertEquals(Decision.REVIEW, service.evaluate(STUDENT_ID, sample(mix(enrolled, 0.50f), 1000)).decision());
        assertEquals(Decision.REJECT, service.evaluate(STUDENT_ID, sample(mix(enrolled, 0.30f), 1000)).decision());
    }

    @Test
    void identicalEmbeddingIsFlaggedAsPossibleReplay() {
        float[] enrolled = unit(0);
        enrol(enrolled);
        FaceService.Evaluation evaluation = service.evaluate(STUDENT_ID, sample(enrolled, 1000));
        assertEquals(Decision.REVIEW, evaluation.decision());
        assertTrue(evaluation.reason().contains("replay"));
    }

    @Test
    void spoofedFaceIsRejectedEvenWhenItMatches() {
        float[] enrolled = unit(0);
        enrol(enrolled);
        assertEquals(Decision.REJECT, service.evaluate(STUDENT_ID, sample(mix(enrolled, 0.9f), 2500)).decision());
    }

    @Test
    void rejectsMalformedSamples() {
        assertThrows(BadRequestException.class, () -> FaceService.parseSample(
                new EnrollFaceRequest(Base64.getEncoder().encodeToString(new byte[10]), b64(JPEG), 0, "sig")));
        assertThrows(BadRequestException.class, () -> FaceService.parseSample(
                new EnrollFaceRequest(b64(bytes(unit(0))), b64(new byte[] {1, 2, 3}), 0, "sig")));
    }

    @Test
    void verifiesDeviceSignatureOverSampleDigest() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = generator.generateKeyPair();
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

        FaceSample sample = FaceService.parseSample(new EnrollFaceRequest(b64(bytes(unit(0))), b64(JPEG), 1234, "unused"));
        String payload = "MARK|5|nonce|" + FaceService.sampleDigest(sample);
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(payload.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(signer.sign());

        assertTrue(DeviceSignatures.verify(publicKey, payload, signature));
        assertFalse(DeviceSignatures.verify(publicKey, payload.replace("|5|", "|6|"), signature));
        assertFalse(DeviceSignatures.verify(publicKey, payload, "bm90LWEtc2lnbmF0dXJl"));
    }

    private void enrol(float[] embedding) {
        User student = new User("student@example.com", "hash", Role.STUDENT);
        when(profiles.findByUserId(STUDENT_ID)).thenReturn(Optional.of(new FaceProfile(student, bytes(embedding), JPEG)));
    }

    private static FaceSample sample(float[] embedding, int spoofScoreBp) {
        return FaceService.parseSample(new EnrollFaceRequest(b64(bytes(embedding)), b64(JPEG), spoofScoreBp, "unused"));
    }

    /** A unit vector whose cosine with [base] is exactly [cosine] (base must be axis 0). */
    private static float[] mix(float[] base, float cosine) {
        float[] result = new float[FaceService.EMBEDDING_SIZE];
        result[0] = cosine * base[0];
        result[1] = (float) Math.sqrt(1 - cosine * cosine);
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
