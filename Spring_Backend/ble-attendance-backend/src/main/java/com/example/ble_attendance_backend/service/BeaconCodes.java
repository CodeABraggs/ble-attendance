package com.example.ble_attendance_backend.service;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Rotating codes broadcast by the teacher's BLE beacon. The app computes the same values in
 * BeaconCodes.kt, so the two must stay in sync: HMAC-SHA256(secret, sessionId || window), first 4 bytes.
 */
public final class BeaconCodes {
    public static final long WINDOW_SECONDS = 30;
    // Tolerates clock drift between the teacher's phone and the server.
    private static final int ALLOWED_DRIFT_WINDOWS = 2;
    private static final SecureRandom RANDOM = new SecureRandom();

    private BeaconCodes() {
    }

    public static String newSecret() {
        byte[] secret = new byte[16];
        RANDOM.nextBytes(secret);
        return HexFormat.of().formatHex(secret);
    }

    public static boolean verify(String secretHex, long sessionId, int code, Instant now) {
        long currentWindow = now.getEpochSecond() / WINDOW_SECONDS;
        for (long window = currentWindow - ALLOWED_DRIFT_WINDOWS; window <= currentWindow + ALLOWED_DRIFT_WINDOWS; window++) {
            if (code(secretHex, sessionId, window) == code) {
                return true;
            }
        }
        return false;
    }

    static int code(String secretHex, long sessionId, long window) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(HexFormat.of().parseHex(secretHex), "HmacSHA256"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(16).putLong(sessionId).putLong(window).array());
            return ByteBuffer.wrap(hash, 0, 4).getInt();
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Could not compute beacon code", exception);
        }
    }
}
