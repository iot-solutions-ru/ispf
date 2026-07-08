package com.ispf.server.application.bundle;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BL-185: stub symbol marketplace catalog for dev/lab (remote install planned).
 */
@Service
public class MarketplaceSymbolListingService {

    public Map<String, Object> listSymbolPacks() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "OK");
        response.put("source", "stub");
        response.put("count", 1);
        response.put("listings", List.of(referencePidPack()));
        return response;
    }

    private static Map<String, Object> referencePidPack() {
        Map<String, Object> listing = new LinkedHashMap<>();
        listing.put("slug", "ispf-pid-v1");
        listing.put("title", "P&ID Symbol Library v1");
        listing.put("description", "Platform reference ISA/ISO functional P&ID symbol pack for mimic editor.");
        listing.put("artifactKind", "symbol-pack");
        listing.put("pricing", "free");
        listing.put("packId", "ispf-pid-v1");
        listing.put("version", "2.0.0");
        listing.put("latestVersion", "2.0.0");
        listing.put("license", "Apache-2.0");
        listing.put("symbolCount", 218);
        listing.put("categories", List.of("equipment", "valves", "instruments", "piping"));
        listing.put("vendorName", "ISPF Core");
        listing.put("vendorLegalName", "IoT Solutions Platform Contributors");
        listing.put("minIspfVersion", "0.9.30");
        listing.put("tags", List.of("pid", "scada", "reference"));
        return listing;
    }
}
