package com.ispf.server.security.mfa;

import com.ispf.server.config.IspfSecurityProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * TOTP MFA enrollment with persisted secrets (BL-153).
 */
@Service
public class MfaService {

    private static final int DEFAULT_TIME_WINDOW = 1;

    private final IspfSecurityProperties securityProperties;
    private final MfaEnrollmentStore enrollmentStore;
    private final Clock clock;

    @Autowired
    public MfaService(IspfSecurityProperties securityProperties, MfaEnrollmentStore enrollmentStore) {
        this(securityProperties, enrollmentStore, Clock.systemUTC());
    }

    MfaService(IspfSecurityProperties securityProperties, MfaEnrollmentStore enrollmentStore, Clock clock) {
        this.securityProperties = securityProperties;
        this.enrollmentStore = enrollmentStore;
        this.clock = clock;
    }

    public MfaStatus status(String username) {
        if (username == null || username.isBlank()) {
            return new MfaStatus(securityProperties.getMfa().isEnabled(), false, false, null, null, null);
        }
        return enrollmentStore.findByUsername(username)
                .map(enrollment -> {
                    boolean pending = enrollment.isPending();
                    String otpauth = pending
                            ? TotpUtil.buildOtpauthUri("ISPF", username, enrollment.secret())
                            : null;
                    return new MfaStatus(
                            securityProperties.getMfa().isEnabled(),
                            enrollment.isEnrolled(),
                            pending,
                            pending ? enrollment.createdAt().toString() : null,
                            pending ? enrollment.secret() : null,
                            otpauth
                    );
                })
                .orElseGet(() -> new MfaStatus(
                        securityProperties.getMfa().isEnabled(),
                        false,
                        false,
                        null,
                        null,
                        null
                ));
    }

    public EnrollmentStart startEnrollment(String username) {
        requireMfaEnabled();
        String secret = TotpUtil.generateSecret();
        Instant startedAt = clock.instant();
        enrollmentStore.savePending(username, secret, startedAt);
        String label = username != null ? username : "user";
        String otpauthUri = TotpUtil.buildOtpauthUri("ISPF", label, secret);
        return new EnrollmentStart(
                secret,
                otpauthUri,
                "Scan the OTP URI with an authenticator app, then POST /verify with a 6-digit code."
        );
    }

    public EnrollmentVerifyResult verifyEnrollment(String username, String code) {
        requireMfaEnabled();
        MfaEnrollmentStore.MfaEnrollment pending = enrollmentStore.findByUsername(username)
                .filter(MfaEnrollmentStore.MfaEnrollment::isPending)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No pending MFA enrollment"));
        if (!TotpUtil.verifyCode(pending.secret(), code, timeWindowSteps(), clock.instant())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid TOTP code");
        }
        enrollmentStore.confirmEnrollment(username, clock.instant());
        return new EnrollmentVerifyResult(true, "MFA enrollment recorded");
    }

    public void cancelEnrollment(String username) {
        enrollmentStore.deletePending(username);
    }

    /**
     * Enforces MFA for admin login when {@code ispf.security.mfa.required-for-admin=true}.
     */
    public void requireAdminLoginCode(String username, List<String> roles, String totpCode) {
        if (!securityProperties.getMfa().isEnabled()
                || !securityProperties.getMfa().isRequiredForAdmin()
                || roles == null
                || !roles.contains("admin")) {
            return;
        }
        MfaEnrollmentStore.MfaEnrollment enrollment = enrollmentStore.findByUsername(username)
                .filter(MfaEnrollmentStore.MfaEnrollment::isEnrolled)
                .orElseThrow(() -> new IllegalArgumentException("Admin MFA enrollment required"));
        if (totpCode == null || totpCode.isBlank()) {
            throw new IllegalArgumentException("MFA TOTP code required");
        }
        if (!TotpUtil.verifyCode(enrollment.secret(), totpCode.trim(), timeWindowSteps(), clock.instant())) {
            throw new IllegalArgumentException("Invalid TOTP code");
        }
    }

    private int timeWindowSteps() {
        int configured = securityProperties.getMfa().getTimeWindowSteps();
        return configured >= 0 ? configured : DEFAULT_TIME_WINDOW;
    }

    private void requireMfaEnabled() {
        if (!securityProperties.getMfa().isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "MFA is not enabled on this installation");
        }
    }

    public record MfaStatus(
            boolean enabled,
            boolean enrolled,
            boolean enrollmentPending,
            String enrollmentStartedAt,
            /** Present only while enrollment is pending — lets the console resume QR after reload. */
            String pendingSecret,
            String pendingOtpauthUri
    ) {
    }

    public record EnrollmentStart(String secret, String otpauthUri, String hint) {
    }

    public record EnrollmentVerifyResult(boolean enrolled, String message) {
    }
}
