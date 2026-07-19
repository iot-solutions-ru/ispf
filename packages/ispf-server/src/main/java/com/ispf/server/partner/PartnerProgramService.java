package com.ispf.server.partner;

import com.ispf.server.persistence.PartnerDirectoryRepository;
import com.ispf.server.persistence.PartnerEnrollmentRepository;
import com.ispf.server.persistence.entity.PartnerDirectoryEntity;
import com.ispf.server.persistence.entity.PartnerEnrollmentEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * BL-184: partner program directory and enrollment persistence (Partner Portal remains external).
 */
@Service
public class PartnerProgramService {

    private static final String PORTAL_URL = "https://github.com/your-org/Partner-portal";
    private static final AtomicLong APPLICATION_SEQ = new AtomicLong(1);

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

    private final PartnerDirectoryRepository partnerDirectoryRepository;
    private final PartnerEnrollmentRepository partnerEnrollmentRepository;
    private final ObjectMapper objectMapper;

    public PartnerProgramService(
            PartnerDirectoryRepository partnerDirectoryRepository,
            PartnerEnrollmentRepository partnerEnrollmentRepository,
            ObjectMapper objectMapper
    ) {
        this.partnerDirectoryRepository = partnerDirectoryRepository;
        this.partnerEnrollmentRepository = partnerEnrollmentRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> listExternalPartners() {
        ensureDemoPartnersSeeded();
        List<Map<String, Object>> partners = partnerDirectoryRepository.findAllByOrderByIdAsc().stream()
                .map(this::toPartnerMap)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "OK");
        response.put("source", "db");
        response.put("count", partners.size());
        response.put("partners", partners);
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

    @Transactional
    public Map<String, Object> enroll(PartnerEnrollRequest request) {
        String requestedTier = request != null && request.tierId() != null && !request.tierId().isBlank()
                ? request.tierId()
                : "bronze";
        final String tierId = TIERS.stream().anyMatch(tier -> requestedTier.equals(tier.get("id")))
                ? requestedTier
                : "bronze";

        String applicationId = "partner-app-" + APPLICATION_SEQ.getAndIncrement() + "-"
                + UUID.randomUUID().toString().substring(0, 8);

        PartnerEnrollmentEntity entity = new PartnerEnrollmentEntity();
        entity.setApplicationId(applicationId);
        entity.setTierId(tierId);
        entity.setStatus("ACCEPTED");
        entity.setCreatedAt(Instant.now());
        if (request != null) {
            entity.setCompanyName(blankToNull(request.companyName()));
            entity.setContactEmail(blankToNull(request.contactEmail()));
            entity.setVerticalsJson(writeStringList(request.verticals()));
            entity.setRegionsJson(writeStringList(request.regions()));
        }
        partnerEnrollmentRepository.save(entity);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ACCEPTED");
        response.put("applicationId", applicationId);
        response.put("tierId", tierId);
        response.put("source", "db");
        response.put("portalUrl", PORTAL_URL);
        response.put(
                "message",
                "Enrollment recorded in platform database; sync with Partner Portal pending Phase 32 GA"
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

    private void ensureDemoPartnersSeeded() {
        if (partnerDirectoryRepository.count() > 0) {
            return;
        }
        List<PartnerDirectoryEntity> seed = new ArrayList<>();
        seed.add(demoPartner(
                "acme-integrators",
                "Acme Integrators",
                "Professional",
                "silver",
                List.of("EMEA", "CIS"),
                List.of("scada", "hvac"),
                "https://marketplace.acme.example",
                "2026-01"
        ));
        seed.add(demoPartner(
                "nordic-automation",
                "Nordic Automation AS",
                "Expert",
                "gold",
                List.of("Nordics", "Baltics"),
                List.of("mes", "warehouse"),
                "https://catalog.nordic-automation.example",
                "2025-11"
        ));
        seed.add(demoPartner(
                "pacific-ot",
                "Pacific OT Solutions",
                "Professional",
                "silver",
                List.of("APAC"),
                List.of("scada", "pipeline"),
                "https://ispf.pacific-ot.example",
                "2026-03"
        ));
        partnerDirectoryRepository.saveAll(seed);
    }

    private PartnerDirectoryEntity demoPartner(
            String id,
            String name,
            String certificationLevel,
            String tierId,
            List<String> regions,
            List<String> verticals,
            String marketplaceUrl,
            String certifiedSince
    ) {
        PartnerDirectoryEntity entity = new PartnerDirectoryEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setCertificationLevel(certificationLevel);
        entity.setTierId(tierId);
        entity.setRegionsJson(writeStringList(regions));
        entity.setVerticalsJson(writeStringList(verticals));
        entity.setMarketplaceUrl(marketplaceUrl);
        entity.setCertifiedSince(certifiedSince);
        entity.setStatus("certified");
        return entity;
    }

    private Map<String, Object> toPartnerMap(PartnerDirectoryEntity entity) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", entity.getId());
        row.put("name", entity.getName());
        row.put("certificationLevel", entity.getCertificationLevel());
        row.put("tierId", entity.getTierId());
        row.put("regions", readStringList(entity.getRegionsJson()));
        row.put("verticals", readStringList(entity.getVerticalsJson()));
        row.put("marketplaceUrl", entity.getMarketplaceUrl());
        row.put("certifiedSince", entity.getCertifiedSince());
        row.put("status", entity.getStatus());
        return row;
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            String[] values = objectMapper.readValue(json, String[].class);
            return List.of(values);
        } catch (JacksonException e) {
            return List.of();
        }
    }

    private String writeStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(values.toArray(new String[0]));
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize string list", e);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
