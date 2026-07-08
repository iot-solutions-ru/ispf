package com.ispf.server.security.mfa;

import com.ispf.server.config.IspfSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MfaAdminLoginEnforcementTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-07-07T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    @Mock
    private MfaEnrollmentStore enrollmentStore;

    private IspfSecurityProperties properties;
    private MfaService mfaService;

    @BeforeEach
    void setUp() {
        properties = new IspfSecurityProperties();
        mfaService = new MfaService(properties, enrollmentStore, FIXED_CLOCK);
    }

    @Test
    void skipsWhenRequiredForAdminDisabled() {
        properties.getMfa().setEnabled(true);
        assertThatCode(() -> mfaService.requireAdminLoginCode("admin", List.of("admin"), null))
                .doesNotThrowAnyException();
    }

    @Test
    void requiresEnrollmentAndTotpForAdmin() {
        properties.getMfa().setEnabled(true);
        properties.getMfa().setRequiredForAdmin(true);
        String secret = TotpUtil.generateSecret();
        when(enrollmentStore.findByUsername("admin")).thenReturn(Optional.of(
                new MfaEnrollmentStore.MfaEnrollment(
                        "admin",
                        secret,
                        FIXED_INSTANT,
                        FIXED_INSTANT
                )
        ));

        assertThatThrownBy(() -> mfaService.requireAdminLoginCode("admin", List.of("admin"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TOTP code required");

        String code = TotpUtil.generateCode(secret, FIXED_INSTANT);
        assertThatCode(() -> mfaService.requireAdminLoginCode("admin", List.of("admin"), code))
                .doesNotThrowAnyException();
    }

    @Test
    void operatorLoginUnaffected() {
        properties.getMfa().setEnabled(true);
        properties.getMfa().setRequiredForAdmin(true);
        assertThatCode(() -> mfaService.requireAdminLoginCode("operator", List.of("operator"), null))
                .doesNotThrowAnyException();
    }
}
