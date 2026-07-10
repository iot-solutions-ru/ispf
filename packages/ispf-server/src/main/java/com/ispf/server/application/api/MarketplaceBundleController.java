package com.ispf.server.application.api;

import com.ispf.server.application.bundle.MarketplaceLocalBundleService;
import com.ispf.server.application.bundle.MarketplaceAnalyticsPackLocalService;
import com.ispf.server.application.bundle.MarketplaceSymbolListingService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * BL-183: local marketplace bundle browse + install for dev/lab (offline/air-gapped).
 */
@RestController
@RequestMapping("/api/v1/marketplace")
public class MarketplaceBundleController {

    private final MarketplaceLocalBundleService localBundleService;
    private final MarketplaceAnalyticsPackLocalService analyticsPackLocalService;
    private final MarketplaceSymbolListingService symbolListingService;

    public MarketplaceBundleController(
            MarketplaceLocalBundleService localBundleService,
            MarketplaceAnalyticsPackLocalService analyticsPackLocalService,
            MarketplaceSymbolListingService symbolListingService
    ) {
        this.localBundleService = localBundleService;
        this.analyticsPackLocalService = analyticsPackLocalService;
        this.symbolListingService = symbolListingService;
    }

    @GetMapping("/bundles")
    public Map<String, Object> listBundles() {
        return localBundleService.listLocalBundles();
    }

    /** BL-185: symbol pack marketplace listing stub. */
    @GetMapping("/symbols")
    public Map<String, Object> listSymbolPacks() {
        return symbolListingService.listSymbolPacks();
    }

    @PostMapping("/symbols/{id}/install")
    public Map<String, Object> installSymbolPack(@PathVariable("id") String packId) {
        try {
            return symbolListingService.installSymbolPack(packId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping("/analytics-packs")
    public Map<String, Object> listAnalyticsPacks() {
        return analyticsPackLocalService.listLocalPacks();
    }

    @PostMapping("/analytics-packs/{id}/install")
    public Map<String, Object> installAnalyticsPack(@PathVariable("id") String packId) {
        try {
            Map<String, Object> result = analyticsPackLocalService.installLocalPack(packId);
            if ("ERROR".equals(result.get("status"))) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        String.valueOf(result.getOrDefault("errors", "Analytics pack validation failed"))
                );
            }
            return result;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }

    @PostMapping("/bundles/{id}/install")
    public Map<String, Object> installBundle(@PathVariable("id") String bundleId) {
        try {
            Map<String, Object> result = localBundleService.installLocalBundle(bundleId);
            if ("ERROR".equals(result.get("status"))) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        String.valueOf(result.getOrDefault("errors", "Bundle validation failed"))
                );
            }
            return result;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }

    @DeleteMapping("/bundles/{id}/install")
    public Map<String, Object> uninstallBundle(@PathVariable("id") String bundleId) {
        try {
            Map<String, Object> result = localBundleService.uninstallLocalBundle(bundleId);
            if ("ERROR".equals(result.get("status"))) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        String.valueOf(result.getOrDefault("errors", "Bundle validation failed"))
                );
            }
            return result;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }
}
