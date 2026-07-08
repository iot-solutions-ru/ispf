package com.ispf.server.partner;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * BL-184: partner program tier catalog stub for dev/lab and partner portal foundation.
 */
@Service
public class PartnerProgramService {

    private static final String PORTAL_URL = "https://github.com/Michaael/Partner-portal";
    private static final AtomicLong APPLICATION_SEQ = new AtomicLong(1);

    private static final List<Map<String, Object>> EXTERNAL_PARTNERS = List.of(
            externalPartner(
                    "acme-integrators",
                    "Acme Integrators",
                    "Professional",
                    "silver",
                    List.of("EMEA", "CIS"),
                    List.of("scada", "hvac"),
                    "https://marketplace.acme.example",
                    "2026-01"
            ),
            externalPartner(
                    "nordic-automation",
                    "Nordic Automation AS",
                    "Expert",
                    "gold",
                    List.of("Nordics", "Baltics"),
                    List.of("mes", "warehouse"),
                    "https://catalog.nordic-automation.example",
                    "2025-11"
            ),
            externalPartner(
                    "pacific-ot",
                    "Pacific OT Solutions",
                    "Professional",
                    "silver",
                    List.of("APAC"),
                    List.of("scada", "pipeline"),
                    "https://ispf.pacific-ot.example",
                    "2026-03"
            )
    );

    private static final List<Map<String, Object>> TIERS = List.of(
            tier(
                    "bronze",
                    "Bronze",
                    "Entry certified integrator — object tree, bundles, operator UI deploy.",
                    0,
                    List.of("Partner directory listing", "Training curriculum access")
            ),
            tier(
                    "silver",
                    "Silver",
                    "Solution integrator — drivers, dashboards, automation, federation basics.",
                    1,
                    List.of(
                            "Marketplace revenue share",
                            "Early access builds",
                            "Co-marketing eligibility"
                    )
            ),
            tier(
                    "gold",
                    "Gold",
                    "Lead architect / OEM partner — cluster, historian tiers, MES bundle, agent playbooks.",
                    2,
                    List.of(
                            "Priority support channel",
                            "Co-marketing / case studies",
                            "Driver pack co-signing (OEM track)"
                    )
            )
    );

    public Map<String, Object> listExternalPartners() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "OK");
        response.put("source", "stub");
        response.put("count", EXTERNAL_PARTNERS.size());
        response.put("partners", EXTERNAL_PARTNERS);
        response.put("portalUrl", PORTAL_URL);
        return response;
    }

    public Map<String, Object> listTiers() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "OK");
        response.put("count", TIERS.size());
        response.put("tiers", TIERS);
        return response;
    }

    public Map<String, Object> enroll(PartnerEnrollRequest request) {
        String requestedTier = request != null && request.tierId() != null && !request.tierId().isBlank()
                ? request.tierId()
                : "bronze";
        final String tierId = TIERS.stream().anyMatch(tier -> requestedTier.equals(tier.get("id")))
                ? requestedTier
                : "bronze";

        String applicationId = "partner-app-" + APPLICATION_SEQ.getAndIncrement() + "-" + UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ACCEPTED");
        response.put("applicationId", applicationId);
        response.put("tierId", tierId);
        response.put("portalUrl", PORTAL_URL);
        response.put(
                "message",
                "Enrollment recorded in platform stub; sync with Partner Portal pending Phase 32 GA"
        );
        if (request != null) {
            if (request.companyName() != null && !request.companyName().isBlank()) {
                response.put("companyName", request.companyName());
            }
            if (request.contactEmail() != null && !request.contactEmail().isBlank()) {
                response.put("contactEmail", request.contactEmail());
            }
            if (request.verticals() != null && !request.verticals().isEmpty()) {
                response.put("verticals", request.verticals());
            }
            if (request.regions() != null && !request.regions().isEmpty()) {
                response.put("regions", request.regions());
            }
        }
        return response;
    }

    public record PartnerEnrollRequest(
            String companyName,
            String contactEmail,
            String tierId,
            List<String> verticals,
            List<String> regions
    ) {
    }

    private static Map<String, Object> externalPartner(
            String id,
            String name,
            String certificationLevel,
            String tierId,
            List<String> regions,
            List<String> verticals,
            String marketplaceUrl,
            String certifiedSince
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", name);
        row.put("certificationLevel", certificationLevel);
        row.put("tierId", tierId);
        row.put("regions", regions);
        row.put("verticals", verticals);
        row.put("marketplaceUrl", marketplaceUrl);
        row.put("certifiedSince", certifiedSince);
        row.put("status", "certified");
        return row;
    }

    private static Map<String, Object> tier(
            String id,
            String name,
            String description,
            int minProductionSites,
            List<String> benefits
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", name);
        row.put("description", description);
        row.put("minProductionSites", minProductionSites);
        row.put("benefits", benefits);
        return row;
    }
}
