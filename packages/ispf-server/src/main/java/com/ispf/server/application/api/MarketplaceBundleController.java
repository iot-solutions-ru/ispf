package com.ispf.server.application.api;

import com.ispf.server.application.bundle.MarketplaceLocalBundleService;
import org.springframework.http.HttpStatus;
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

    public MarketplaceBundleController(MarketplaceLocalBundleService localBundleService) {
        this.localBundleService = localBundleService;
    }

    @GetMapping("/bundles")
    public Map<String, Object> listBundles() {
        return localBundleService.listLocalBundles();
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
}
