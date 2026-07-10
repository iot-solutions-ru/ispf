package com.ispf.server.application.api;

import com.ispf.server.application.bundle.MarketplaceService;
import com.ispf.server.application.bundle.SolutionCatalogService;
import com.ispf.server.license.CommercialLicenseException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/solutions")
public class SolutionCatalogController {

    private final SolutionCatalogService catalogService;
    private final MarketplaceService marketplaceService;

    public SolutionCatalogController(
            SolutionCatalogService catalogService,
            MarketplaceService marketplaceService
    ) {
        this.catalogService = catalogService;
        this.marketplaceService = marketplaceService;
    }

    @GetMapping("/catalog")
    public Map<String, Object> catalog() {
        return catalogService.catalog();
    }

    @DeleteMapping("/installed/applications/{appId}")
    public Map<String, Object> uninstallApplication(@PathVariable String appId) {
        try {
            return catalogService.uninstallApplication(appId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }

    @DeleteMapping("/installed/analytics-packs/{packId}")
    public Map<String, Object> uninstallAnalyticsPack(@PathVariable String packId) {
        try {
            return catalogService.uninstallAnalyticsPack(packId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }

    @GetMapping("/marketplaces")
    public Map<String, Object> marketplaces() {
        return marketplaceService.listMarketplaces();
    }

    @GetMapping("/marketplaces/{marketplaceId}/catalog")
    public Map<String, Object> marketplaceCatalog(
            @PathVariable String marketplaceId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "all") String pricing,
            @RequestParam(required = false, defaultValue = "all") String kind
    ) {
        try {
            return marketplaceService.browseCatalog(marketplaceId, q, pricing, kind);
        } catch (MarketplaceService.MarketplaceRemoteException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }

    @PostMapping("/marketplaces/{marketplaceId}/listings/{slug}/install")
    public Map<String, Object> installMarketplaceListing(
            @PathVariable String marketplaceId,
            @PathVariable String slug
    ) {
        try {
            return marketplaceService.installFreeListing(marketplaceId, slug);
        } catch (CommercialLicenseException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage(), ex);
        } catch (MarketplaceService.MarketplaceRemoteException ex) {
            throw mapRemote(ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }

    @PostMapping("/marketplaces/{marketplaceId}/listings/{slug}/activate")
    public Map<String, Object> activateMarketplaceListing(
            @PathVariable String marketplaceId,
            @PathVariable String slug,
            @RequestBody Map<String, String> body
    ) {
        try {
            String activationCode = body != null ? body.get("activationCode") : null;
            return marketplaceService.activatePaidListing(marketplaceId, slug, activationCode);
        } catch (CommercialLicenseException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage(), ex);
        } catch (MarketplaceService.MarketplaceRemoteException ex) {
            throw mapRemote(ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }

    private static ResponseStatusException mapRemote(MarketplaceService.MarketplaceRemoteException ex) {
        HttpStatus status = ex.statusCode() == 403
                ? HttpStatus.FORBIDDEN
                : ex.statusCode() == 404
                        ? HttpStatus.NOT_FOUND
                        : HttpStatus.BAD_GATEWAY;
        return new ResponseStatusException(status, ex.getMessage(), ex);
    }
}
