package com.ispf.server.platform.analytics.engine;

import com.ispf.analytics.engine.AnalyticsEvaluationOptions;
import com.ispf.analytics.engine.AnalyticsEvaluationResult;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.analytics.AnalyticsClusterWorkloadService;
import com.ispf.server.security.acl.VariableMemberAccessService;
import org.springframework.security.core.Authentication;
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
    private final VariableMemberAccessService variableMemberAccessService;

    public AnalyticsBackfillService(
            AnalyticsTagCatalogService catalogService,
            AnalyticsEngineService engineService,
            ObjectManager objectManager,
            AnalyticsClusterWorkloadService analyticsClusterWorkloadService,
            VariableMemberAccessService variableMemberAccessService
    ) {
        this.catalogService = catalogService;
        this.engineService = engineService;
        this.objectManager = objectManager;
        this.analyticsClusterWorkloadService = analyticsClusterWorkloadService;
        this.variableMemberAccessService = variableMemberAccessService;
    }

    @Transactional
    public BackfillResult backfill(String tagPath, Instant from, Instant to) {
        return backfill(tagPath, from, to, null, false);
    }

    @Transactional
    public BackfillResult backfill(
            String tagPath,
            Instant from,
            Instant to,
            Authentication authentication
    ) {
        return backfill(tagPath, from, to, authentication, true);
    }

    private BackfillResult backfill(
            String tagPath,
            Instant from,
            Instant to,
            Authentication authentication,
            boolean enforceMemberAcl
    ) {
        if (!analyticsClusterWorkloadService.isAnalyticsWorkloadActive()) {
            throw new IllegalStateException(
                    "Analytics backfill is not active on this replica (dedicated analytics replicas exist)");
        }
        if (from == null || to == null || !from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
        catalogService.findCatalogEntryByTagPath(tagPath)
                .orElseThrow(() -> new IllegalArgumentException("Historian computation not found: " + tagPath));

        List<AnalyticsTagDefinition> tags = catalogService.listEnabledTags();
        if (enforceMemberAcl) {
            variableMemberAccessService.requireReadAll(
                    authentication,
                    tags.stream()
                            .flatMap(tag -> tag.sources().stream())
                            .map(source -> new VariableMemberAccessService.VariableRef(
                                    source.path(),
                                    source.variable()
                            ))
                            .distinct()
                            .toList()
            );
        }
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
