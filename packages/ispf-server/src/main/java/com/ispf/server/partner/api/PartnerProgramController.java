package com.ispf.server.partner.api;

import com.ispf.server.partner.PartnerProgramService;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/tiers")
    public Map<String, Object> listTiers() {
        return partnerProgramService.listTiers();
    }
}
