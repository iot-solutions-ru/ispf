package com.ispf.server.platform.analytics.engine;

import com.ispf.analytics.engine.AnalyticsEvaluationOptions;
import com.ispf.analytics.engine.AnalyticsEvaluationResult;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.server.object.ObjectManager;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.platform.analytics.AnalyticsClusterWorkloadService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Recomputes derived tag values for a historian window (BL-204).
 */
@Service
public class AnalyticsBackfillService {

    private final AnalyticsTagCatalogService catalogService;
    private final AnalyticsEngineService engineService;
    private final ObjectManager objectManager;
    private final AnalyticsClusterWorkloadService analyticsClusterWorkloadService;

    public AnalyticsBackfillService(
            AnalyticsTagCatalogService catalogService,
            AnalyticsEngineService engineService,
            ObjectManager objectManager,
            AnalyticsClusterWorkloadService analyticsClusterWorkloadService
    ) {
        this.catalogService = catalogService;
        this.engineService = engineService;
        this.objectManager = objectManager;
        this.analyticsClusterWorkloadService = analyticsClusterWorkloadService;
    }

    @Transactional
    public BackfillResult backfill(String tagPath, Instant from, Instant to) {
        if (!analyticsClusterWorkloadService.isAnalyticsWorkloadActive()) {
            throw new IllegalStateException(
                    "Analytics backfill is not active on this replica (dedicated analytics replicas exist)");
        }
        if (from == null || to == null || !from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
        PlatformObject node = objectManager.require(tagPath);
        catalogService.toTagDefinition(node)
                .orElseThrow(() -> new IllegalArgumentException("Not an analytics derived tag: " + tagPath));

        List<AnalyticsTagDefinition> tags = catalogService.listEnabledTags();
        List<AnalyticsEvaluationResult> results = engineService.evaluateTags(
                tags,
                new AnalyticsEvaluationOptions(to),
                to
        );
        int updated = (int) results.stream().filter(r -> "ok".equals(r.status())).count();
        return new BackfillResult(tagPath, from, to, updated, results);
    }

    public record BackfillResult(
            String tagPath,
            Instant from,
            Instant to,
            int updated,
            List<AnalyticsEvaluationResult> results
    ) {
    }
}
