package com.ispf.server.application.bundle;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * BL-185: stub symbol marketplace catalog for dev/lab (remote install planned).
 */
@Service
public class MarketplaceSymbolListingService {

    private static final AtomicLong INSTALL_SEQ = new AtomicLong(1);
    private final Map<String, Map<String, Object>> installedPacks = new ConcurrentHashMap<>();

    public Map<String, Object> listSymbolPacks() {
        Map<String, Object> response = new LinkedHashMap<>();
        List<Map<String, Object>> listings = catalogListings();
        response.put("status", "OK");
        response.put("source", "stub");
        response.put("count", listings.size());
        response.put("listings", listings);
        response.put("installedCount", installedPacks.size());
        return response;
    }

    public Map<String, Object> installSymbolPack(String packId) {
        if (packId == null || packId.isBlank()) {
            throw new IllegalArgumentException("pack id is required");
        }
        Map<String, Object> listing = findListing(packId.trim());
        if (listing == null) {
            throw new IllegalArgumentException("Unknown symbol pack: " + packId);
        }
        if (!"free".equalsIgnoreCase(String.valueOf(listing.getOrDefault("pricing", "free")))) {
            throw new IllegalArgumentException("Local install supports free symbol packs only");
        }

        String installationId = "symbol-install-" + INSTALL_SEQ.getAndIncrement() + "-"
                + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> installed = new LinkedHashMap<>(listing);
        installed.put("installationId", installationId);
        installed.put("installedAt", java.time.Instant.now().toString());
        installedPacks.put(packId.trim(), installed);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "OK");
        response.put("source", "stub");
        response.put("action", "install");
        response.put("installationId", installationId);
        response.put("packId", listing.get("packId"));
        response.put("slug", listing.get("slug"));
        response.put("version", listing.get("version"));
        response.put("symbolCount", listing.get("symbolCount"));
        response.put(
                "message",
                "Symbol pack registered in platform stub; mimic editor reload pending Phase 32 GA"
        );
        return response;
    }

    private Map<String, Object> findListing(String packId) {
        for (Map<String, Object> listing : catalogListings()) {
            String slug = String.valueOf(listing.get("slug"));
            String id = String.valueOf(listing.get("packId"));
            if (packId.equals(slug) || packId.equals(id)) {
                return listing;
            }
        }
        return null;
    }

    private static List<Map<String, Object>> catalogListings() {
        return List.of(referencePidPack(), hvacEquipmentPack());
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

    private static Map<String, Object> hvacEquipmentPack() {
        Map<String, Object> listing = new LinkedHashMap<>();
        listing.put("slug", "hvac-equipment-v1");
        listing.put("title", "HVAC Equipment Symbols");
        listing.put("description", "AHU, fan coil, damper, and sensor glyphs for building automation mimics.");
        listing.put("artifactKind", "symbol-pack");
        listing.put("pricing", "free");
        listing.put("packId", "hvac-equipment-v1");
        listing.put("version", "1.0.0");
        listing.put("latestVersion", "1.0.0");
        listing.put("license", "Apache-2.0");
        listing.put("symbolCount", 48);
        listing.put("categories", List.of("equipment", "instruments", "hvac"));
        listing.put("vendorName", "Building Apps Lab");
        listing.put("vendorLegalName", "Building Apps Lab Contributors");
        listing.put("minIspfVersion", "0.9.30");
        listing.put("tags", List.of("hvac", "building", "scada"));
        return listing;
    }
}
