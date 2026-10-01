package com.example.ble_attendance_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class BeaconCodesTest {
    private static final String SECRET = "00112233445566778899aabbccddeeff";

    // Same vectors as the app's BeaconCodesTest.kt, so both sides are known to agree.
    @Test
    void matchesSharedTestVectors() {
        assertEquals(-1399218303, BeaconCodes.code(SECRET, 42, 58_000_000));
        assertEquals(172204245, BeaconCodes.code(SECRET, 7, 1));
    }

    @Test
    void acceptsCodesWithinAllowedDriftOnly() {
        Instant now = Instant.ofEpochSecond(58_000_000L * BeaconCodes.WINDOW_SECONDS);
        int current = BeaconCodes.code(SECRET, 42, 58_000_000);
        int twoWindowsAgo = BeaconCodes.code(SECRET, 42, 57_999_998);
        int threeWindowsAgo = BeaconCodes.code(SECRET, 42, 57_999_997);

        assertTrue(BeaconCodes.verify(SECRET, 42, current, now));
        assertTrue(BeaconCodes.verify(SECRET, 42, twoWindowsAgo, now));
        assertFalse(BeaconCodes.verify(SECRET, 42, threeWindowsAgo, now));
        assertFalse(BeaconCodes.verify(SECRET, 43, current, now));
    }
}
