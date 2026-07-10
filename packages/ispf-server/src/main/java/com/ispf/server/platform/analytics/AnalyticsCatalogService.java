package com.ispf.server.platform.analytics;

import com.ispf.server.platform.analytics.catalog.AnalyticsCatalogEntry;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Thin service facade for analytics function catalog lookups (BL-212a).
 */
@Service
public class AnalyticsCatalogService {

    private final AnalyticsCatalogRegistry registry;

    public AnalyticsCatalogService(AnalyticsCatalogRegistry registry) {
        this.registry = registry;
    }

    public List<AnalyticsCatalogEntry> list() {
        return registry.list();
    }

    public AnalyticsCatalogEntry get(String functionId) {
        return registry.findById(functionId)
                .orElseThrow(() -> new IllegalArgumentException("Analytics function not found: " + functionId));
    }
}
