package com.example.ble_attendance_backend.security;

import com.example.ble_attendance_backend.entity.AuthToken;
import com.example.ble_attendance_backend.entity.User;
import com.example.ble_attendance_backend.repository.AuthTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthTokenService {
    public static final Duration TOKEN_LIFETIME = Duration.ofDays(7);

    private final SecureRandom random = new SecureRandom();
    private final AuthTokenRepository tokenRepository;

    public AuthTokenService(AuthTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }

    @Transactional
    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        tokenRepository.deleteExpiredForUser(user.getId(), now);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = now.plus(TOKEN_LIFETIME);
        tokenRepository.save(new AuthToken(hash(token), user, expiresAt));
        return new IssuedToken(token, expiresAt);
    }

    @Transactional
    public Optional<AuthenticatedUser> authenticate(String token) {
        String tokenHash = hash(token);
        Optional<AuthToken> stored = tokenRepository.findByTokenHash(tokenHash);
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        if (stored.get().getExpiresAt().isBefore(Instant.now())) {
            tokenRepository.delete(stored.get());
            return Optional.empty();
        }
        return Optional.of(AuthenticatedUser.from(stored.get().getUser()));
    }

    @Transactional
    public void revoke(String token) {
        tokenRepository.deleteByTokenHash(hash(token));
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
