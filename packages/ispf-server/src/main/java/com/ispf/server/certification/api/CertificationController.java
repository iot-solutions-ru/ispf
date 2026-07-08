package com.ispf.server.certification.api;

import com.ispf.server.certification.CertificationExamService;
import com.ispf.server.certification.CertificationExamService.CertificationAttemptRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * BL-190: certification exam runner stub.
 */
@RestController
@RequestMapping("/api/v1/certification")
public class CertificationController {

    private final CertificationExamService certificationExamService;

    public CertificationController(CertificationExamService certificationExamService) {
        this.certificationExamService = certificationExamService;
    }

    @PostMapping("/attempt")
    public Map<String, Object> submitAttempt(@RequestBody CertificationAttemptRequest request) {
        try {
            return certificationExamService.submitAttempt(request);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), ex);
        }
    }
}
