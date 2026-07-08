package com.ispf.server.partner;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BL-184: partner program tier catalog stub for dev/lab and partner portal foundation.
 */
@Service
public class PartnerProgramService {

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

    public Map<String, Object> listTiers() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "OK");
        response.put("count", TIERS.size());
        response.put("tiers", TIERS);
        return response;
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
