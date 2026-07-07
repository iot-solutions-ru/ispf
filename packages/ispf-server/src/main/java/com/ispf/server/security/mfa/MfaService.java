package com.ispf.server.security.mfa;

import com.ispf.server.config.IspfSecurityProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MFA enrollment skeleton (TOTP). Full verification and persistence are planned for a later sprint.
 */
@Service
public class MfaService {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final IspfSecurityProperties securityProperties;
    private final Map<String, PendingEnrollment> pendingByUsername = new ConcurrentHashMap<>();

    public MfaService(IspfSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    public MfaStatus status(String username) {
        PendingEnrollment pending = pendingByUsername.get(username);
        return new MfaStatus(
                securityProperties.getMfa().isEnabled(),
                pending != null,
                pending != null ? pending.createdAt().toString() : null
        );
    }

    public EnrollmentStart startEnrollment(String username) {
        requireMfaEnabled();
        String secret = generateBase32Secret(20);
        PendingEnrollment pending = new PendingEnrollment(secret, Instant.now());
        pendingByUsername.put(username, pending);
        String label = username != null ? username : "user";
        String otpauthUri = "otpauth://totp/ISPF:" + label + "?secret=" + secret + "&issuer=ISPF&algorithm=SHA1&digits=6&period=30";
        return new EnrollmentStart(secret, otpauthUri, "Scan the OTP URI with an authenticator app, then POST /verify with a 6-digit code.");
    }

    public EnrollmentVerifyResult verifyEnrollment(String username, String code) {
        requireMfaEnabled();
        PendingEnrollment pending = pendingByUsername.get(username);
        if (pending == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No pending MFA enrollment");
        }
        if (code == null || !code.matches("\\d{6}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Code must be 6 digits");
        }
        // Stub: accept any well-formed code; production will validate TOTP and persist enrollment.
        pendingByUsername.remove(username);
        return new EnrollmentVerifyResult(true, "MFA enrollment recorded (stub — TOTP validation not yet implemented)");
    }

    public void cancelEnrollment(String username) {
        pendingByUsername.remove(username);
    }

    private void requireMfaEnabled() {
        if (!securityProperties.getMfa().isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "MFA is not enabled on this installation");
        }
    }

    private static String generateBase32Secret(int length) {
        StringBuilder secret = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            secret.append(BASE32_ALPHABET.charAt(RANDOM.nextInt(BASE32_ALPHABET.length())));
        }
        return secret.toString();
    }

    private record PendingEnrollment(String secret, Instant createdAt) {
    }

    public record MfaStatus(boolean enabled, boolean enrollmentPending, String enrollmentStartedAt) {
    }

    public record EnrollmentStart(String secret, String otpauthUri, String hint) {
    }

    public record EnrollmentVerifyResult(boolean enrolled, String message) {
    }
}
