package com.ispf.server.security.mfa;

import com.ispf.server.config.IspfSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MfaServiceTest {

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
    void enrollmentDisabledByDefault() {
        assertThat(mfaService.status("alice").enabled()).isFalse();
        assertThatThrownBy(() -> mfaService.startEnrollment("alice"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not enabled");
    }

    @Test
    void enrollmentReturnsSecretWhenEnabled() {
        properties.getMfa().setEnabled(true);
        MfaService.EnrollmentStart start = mfaService.startEnrollment("alice");
        assertThat(start.secret()).hasSizeGreaterThanOrEqualTo(16);
        assertThat(start.otpauthUri()).contains("otpauth://totp/ISPF:alice");

        ArgumentCaptor<String> secretCaptor = ArgumentCaptor.forClass(String.class);
        verify(enrollmentStore).savePending(eq("alice"), secretCaptor.capture(), eq(FIXED_INSTANT));
        assertThat(secretCaptor.getValue()).isEqualTo(start.secret());

        when(enrollmentStore.findByUsername("alice")).thenReturn(java.util.Optional.of(
                new MfaEnrollmentStore.MfaEnrollment("alice", start.secret(), null, FIXED_INSTANT)
        ));
        assertThat(mfaService.status("alice").enrollmentPending()).isTrue();
    }

    @Test
    void verifyAcceptsValidTotpCode() {
        properties.getMfa().setEnabled(true);
        MfaService.EnrollmentStart start = mfaService.startEnrollment("bob");
        String code = TotpUtil.generateCode(start.secret(), FIXED_INSTANT);

        when(enrollmentStore.findByUsername("bob")).thenReturn(java.util.Optional.of(
                new MfaEnrollmentStore.MfaEnrollment("bob", start.secret(), null, FIXED_INSTANT)
        ));

        MfaService.EnrollmentVerifyResult result = mfaService.verifyEnrollment("bob", code);
        assertThat(result.enrolled()).isTrue();
        verify(enrollmentStore).confirmEnrollment("bob", FIXED_INSTANT);
    }

    @Test
    void verifyRejectsInvalidTotpCode() {
        properties.getMfa().setEnabled(true);
        MfaService.EnrollmentStart start = mfaService.startEnrollment("bob");
        when(enrollmentStore.findByUsername("bob")).thenReturn(java.util.Optional.of(
                new MfaEnrollmentStore.MfaEnrollment("bob", start.secret(), null, FIXED_INSTANT)
        ));

        assertThatThrownBy(() -> mfaService.verifyEnrollment("bob", "000000"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid TOTP code");
    }
}
