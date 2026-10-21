package com.pavti.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Passwords and OTPs are stored only as hashes.
 * BCrypt for passwords/OTPs (slow on purpose), SHA-256 for long random tokens.
 */
@Component
public class PasswordHelper {

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(12);
    private final SecureRandom random = new SecureRandom();

    public String hash(String plain) {
        return bcrypt.encode(plain);
    }

    public boolean matches(String plain, String hash) {
        return hash != null && plain != null && bcrypt.matches(plain, hash);
    }

    /** 6-digit code for SMS. */
    public String newOtp() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    /** Long random token (refresh token). */
    public String newToken() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
