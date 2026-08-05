package com.ispf.server.application.uipack;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adds {@code hostedUiUrl} when a UI pack is installed for the app (ADR-0054).
 */
@Component
public class HostedUiPackLinkEnricher {

    private final DropInUiPackLoader uiPackLoader;

    public HostedUiPackLinkEnricher(DropInUiPackLoader uiPackLoader) {
        this.uiPackLoader = uiPackLoader;
    }

    public Map<String, Object> enrich(String appId, Map<String, Object> ui) {
        if (ui == null) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>(ui);
        if (appId != null && !appId.isBlank() && uiPackLoader.isPackInstalled(appId.trim())) {
            Map<String, Object> summary = uiPackLoader.packSummary(appId.trim());
            copy.put("hostedUiUrl", summary.getOrDefault("hostedUiUrl", "/apps/" + appId.trim() + "/"));
            copy.put("uiPack", summary);
        }
        return copy;
    }
}
