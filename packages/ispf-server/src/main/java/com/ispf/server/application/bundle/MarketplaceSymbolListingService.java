package com.ispf.server.application.bundle;

import com.ispf.server.scada.symbol.DropInSymbolPackLoader;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * BL-185: symbol marketplace catalog — local examples + bundled reference + installed packs.
 */
@Service
public class MarketplaceSymbolListingService {

    private static final AtomicLong INSTALL_SEQ = new AtomicLong(1);

    private final MarketplaceSymbolPackLocalService localService;
    private final DropInSymbolPackLoader symbolPackLoader;

    public MarketplaceSymbolListingService(
            MarketplaceSymbolPackLocalService localService,
            DropInSymbolPackLoader symbolPackLoader
    ) {
        this.localService = localService;
        this.symbolPackLoader = symbolPackLoader;
    }

    public Map<String, Object> listSymbolPacks() {
        List<Map<String, Object>> listings = new ArrayList<>();
        listings.add(bundledPidListing());

        Map<String, Object> local = localService.listLocalPacks();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> localPacks = (List<Map<String, Object>>) local.getOrDefault("packs", List.of());
        for (Map<String, Object> pack : localPacks) {
            if (!"OK".equals(pack.get("validationStatus"))) {
                continue;
            }
            Map<String, Object> listing = new LinkedHashMap<>(pack);
            listing.put("artifactKind", "symbol-pack");
            listings.add(listing);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "OK");
        response.put("source", localPacks.isEmpty() ? "bundled" : "local");
        response.put("count", listings.size());
        response.put("listings", listings);
        response.put("installedCount", symbolPackLoader.listInstalledPacks().size());
        response.put("installed", symbolPackLoader.listInstalledPacks());
        return response;
    }

    public Map<String, Object> installSymbolPack(String packId) {
        if (packId == null || packId.isBlank()) {
            throw new IllegalArgumentException("pack id is required");
        }
        String id = packId.trim();
        if ("ispf-pid-v1".equals(id)) {
            Map<String, Object> response = new LinkedHashMap<>(bundledPidListing());
            response.put("status", "OK");
            response.put("source", "bundled");
            response.put("action", "install");
            response.put("installationId", "symbol-bundled-" + INSTALL_SEQ.getAndIncrement());
            response.put(
                    "message",
                    "Reference pack ispf-pid-v1 ships in the web-console mimic editor; no filesystem install required"
            );
            return response;
        }
        try {
            Map<String, Object> result = localService.installLocalPack(id);
            if ("ERROR".equals(result.get("status"))) {
                throw new IllegalArgumentException(String.valueOf(result.getOrDefault("errors", "install failed")));
            }
            result.put("installationId", "symbol-install-" + INSTALL_SEQ.getAndIncrement() + "-"
                    + UUID.randomUUID().toString().substring(0, 8));
            return result;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    private Map<String, Object> bundledPidListing() {
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
        listing.put("tags", List.of("pid", "scada", "reference", "bundled"));
        listing.put("installed", true);
        listing.put("source", "bundled");
        return listing;
    }
}
