package com.ispf.server.security.mfa;

import com.ispf.server.config.IspfSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MfaServiceTest {

    private IspfSecurityProperties properties;
    private MfaService mfaService;

    @BeforeEach
    void setUp() {
        properties = new IspfSecurityProperties();
        mfaService = new MfaService(properties);
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
        assertThat(start.secret()).hasSize(20);
        assertThat(start.otpauthUri()).contains("otpauth://totp/ISPF:alice");
        assertThat(mfaService.status("alice").enrollmentPending()).isTrue();
    }

    @Test
    void verifyAcceptsSixDigitCodeStub() {
        properties.getMfa().setEnabled(true);
        mfaService.startEnrollment("bob");
        MfaService.EnrollmentVerifyResult result = mfaService.verifyEnrollment("bob", "123456");
        assertThat(result.enrolled()).isTrue();
        assertThat(mfaService.status("bob").enrollmentPending()).isFalse();
    }
}
