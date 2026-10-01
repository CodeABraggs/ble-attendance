package com.example.ble_attendance_backend.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Verifies requests signed by the phone's Android Keystore key (EC P-256, SHA256withECDSA, DER signature).
 * The signed payload strings are built identically in the app's DeviceKey.kt callers.
 */
public final class DeviceSignatures {
    private DeviceSignatures() {
    }

    /** Returns false for a malformed key or signature as well as for a wrong signature. */
    public static boolean verify(String publicKeyBase64, String payload, String signatureBase64) {
        if (publicKeyBase64 == null || signatureBase64 == null) {
            return false;
        }
        try {
            PublicKey key = parsePublicKey(publicKeyBase64);
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(key);
            verifier.update(payload.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            return false;
        }
    }

    public static PublicKey parsePublicKey(String publicKeyBase64) throws GeneralSecurityException {
        return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)));
    }

    public static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
