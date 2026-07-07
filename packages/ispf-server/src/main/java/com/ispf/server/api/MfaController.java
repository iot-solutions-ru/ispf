package com.ispf.server.api;

import com.ispf.server.audit.AuditEventService;
import com.ispf.server.security.mfa.MfaService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/security/mfa")
public class MfaController {

    private final MfaService mfaService;
    private final AuditEventService auditEventService;

    public MfaController(MfaService mfaService, AuditEventService auditEventService) {
        this.mfaService = mfaService;
        this.auditEventService = auditEventService;
    }

    @GetMapping("/status")
    public MfaService.MfaStatus status(Authentication authentication) {
        return mfaService.status(authentication != null ? authentication.getName() : null);
    }

    @PostMapping("/enroll")
    public MfaService.EnrollmentStart enroll(Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "anonymous";
        MfaService.EnrollmentStart start = mfaService.startEnrollment(username);
        auditEventService.logMfaEnrollmentStarted(username);
        return start;
    }

    @PostMapping("/verify")
    public MfaService.EnrollmentVerifyResult verify(
            Authentication authentication,
            @RequestBody VerifyRequest request
    ) {
        String username = authentication != null ? authentication.getName() : "anonymous";
        MfaService.EnrollmentVerifyResult result = mfaService.verifyEnrollment(username, request.code());
        auditEventService.logMfaEnrollmentVerified(username);
        return result;
    }

    @DeleteMapping("/enroll")
    public void cancelEnroll(Authentication authentication) {
        String username = authentication != null ? authentication.getName() : null;
        if (username != null) {
            mfaService.cancelEnrollment(username);
            auditEventService.logMfaEnrollmentCancelled(username);
        }
    }

    public record VerifyRequest(@NotBlank String code) {
    }
}
