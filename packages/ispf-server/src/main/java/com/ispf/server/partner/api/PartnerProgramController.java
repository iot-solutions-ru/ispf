package com.ispf.server.partner.api;

import com.ispf.server.partner.PartnerProgramService;
import com.ispf.server.partner.PartnerProgramService.PartnerEnrollRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * BL-184: deprecated — use Partner Portal service ({@code https://github.com/Michaael/Partner-portal}).
 * Kept for backward compatibility until platform proxies external tiers URL.
 */
@RestController
@RequestMapping("/api/v1/partners")
public class PartnerProgramController {

    private final PartnerProgramService partnerProgramService;

    public PartnerProgramController(PartnerProgramService partnerProgramService) {
        this.partnerProgramService = partnerProgramService;
    }

    @GetMapping
    public Map<String, Object> listPartners() {
        return partnerProgramService.listExternalPartners();
    }

    @GetMapping("/tiers")
    public Map<String, Object> listTiers() {
        return partnerProgramService.listTiers();
    }

    @PostMapping("/enroll")
    public ResponseEntity<Map<String, Object>> enroll(@RequestBody(required = false) PartnerEnrollRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(partnerProgramService.enroll(request));
    }
}
