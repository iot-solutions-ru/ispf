package com.ispf.server.history;

import com.ispf.core.object.Variable;
import com.ispf.server.config.MssqlRelationalProperties;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.relational.RelationalDbKind;
import com.ispf.server.relational.RelationalDialect;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Gate C2 follow-up: report-only read of {@code variable_sample_rollups} (ingest / {@code sampled_at}).
 * Not a chart cutover. Not a SWITCH seal. Operator charts stay on raw {@code observed_at}.
 */
public class JdbcMssqlIngestRollupReportService {

    public static final String AXIS_SAMPLED_AT = "sampled_at";
    public static final String AXIS_OBSERVED_AT = "observed_at";
    public static final String SOURCE_ROLLUPS = "variable_sample_rollups";
    public static final String SOURCE_RAW = "raw_variable_samples";
    static final int MAX_REPORT_BUCKETS = 2_000;

    private final JdbcVariableSampleRollupStore rollupStore;
    private final JdbcMssqlHistorianRollupMaterializer materializer;
    private final VariableHistoryService variableHistoryService;
    private final MssqlRelationalProperties mssqlProperties;
    private final RelationalDialect relationalDialect;
    private final ObjectManager objectManager;

    public JdbcMssqlIngestRollupReportService(
            JdbcVariableSampleRollupStore rollupStore,
            JdbcMssqlHistorianRollupMaterializer materializer,
            VariableHistoryService variableHistoryService,
            MssqlRelationalProperties mssqlProperties,
            RelationalDialect relationalDialect,
            ObjectManager objectManager
    ) {
        this.rollupStore = rollupStore;
        this.materializer = materializer;
        this.variableHistoryService = variableHistoryService;
        this.mssqlProperties = mssqlProperties;
        this.relationalDialect = relationalDialect;
        this.objectManager = objectManager;
    }

    public IngestRollupReportResponse report(
            String objectPath,
            String variableName,
            String fieldName,
            Instant from,
            Instant to,
            String bucketSpec,
            int limit
    ) {
        String field = fieldName == null || fieldName.isBlank() ? "value" : fieldName;
        if (limit < 0) {
            throw new IllegalArgumentException("History limit must not be negative");
        }
        Optional<Integer> grain = materializedGrainSeconds(bucketSpec);
        if (grain.isEmpty()) {
            return fallback(objectPath, variableName, field, from, to, bucketSpec, limit, "unsupported_grain", null);
        }
        if (!mssqlProperties.isSingleEngine() || relationalDialect.kind() != RelationalDbKind.MSSQL) {
            return fallback(objectPath, variableName, field, from, to, bucketSpec, limit, "single_engine_required", null);
        }
        var object = objectManager.require(objectPath);
        Optional<Variable> variable = object.getVariable(variableName);
        if (variable.isEmpty()) {
            return emptyRollup(objectPath, variableName, field, bucketSpec, null);
        }
        if (!variable.get().historyEnabled()) {
            return emptyRollup(objectPath, variableName, field, bucketSpec, null);
        }
        if (limit == 0) {
            return emptyRollup(objectPath, variableName, field, bucketSpec, null);
        }

        int bucketSeconds = grain.get();
        Optional<Instant> covered = materializer.loadCoveredThrough(bucketSeconds);
        if (covered.isEmpty()) {
            return fallback(objectPath, variableName, field, from, to, bucketSpec, limit, "no_cursor", null);
        }
        Instant coveredThrough = JdbcMssqlHistorianRollupMaterializer.floorToBucket(
                covered.get(), bucketSeconds
        );
        Instant rangeFrom = from == null ? Instant.EPOCH : from;
        Instant rangeTo = to == null ? coveredThrough : to;
        if (rangeFrom.isAfter(rangeTo)) {
            return emptyRollup(objectPath, variableName, field, bucketSpec, coveredThrough);
        }
        Instant rollupToExclusive = rangeTo.isBefore(coveredThrough) ? rangeTo : coveredThrough;
        if (!rollupToExclusive.isAfter(rangeFrom)) {
            // Empty closed-bucket window (e.g. from >= cursor). Stay on sampled_at axis; do not flip to observed_at.
            return emptyRollup(objectPath, variableName, field, bucketSpec, coveredThrough);
        }
        int capped = Math.min(limit, MAX_REPORT_BUCKETS);
        List<VariableHistoryService.VariableHistoryBucket> buckets = rollupStore.queryBucketsBefore(
                objectPath,
                variableName,
                field,
                rangeFrom,
                rollupToExclusive,
                bucketSeconds,
                capped
        );
        return new IngestRollupReportResponse(
                objectPath,
                variableName,
                field,
                normalizeBucket(bucketSpec),
                AXIS_SAMPLED_AT,
                SOURCE_ROLLUPS,
                coveredThrough,
                null,
                buckets
        );
    }

    private IngestRollupReportResponse emptyRollup(
            String objectPath,
            String variableName,
            String field,
            String bucketSpec,
            Instant coveredThrough
    ) {
        return new IngestRollupReportResponse(
                objectPath,
                variableName,
                field,
                normalizeBucket(bucketSpec == null ? "1h" : bucketSpec),
                AXIS_SAMPLED_AT,
                SOURCE_ROLLUPS,
                coveredThrough,
                null,
                List.of()
        );
    }

    private IngestRollupReportResponse fallback(
            String objectPath,
            String variableName,
            String fieldName,
            Instant from,
            Instant to,
            String bucketSpec,
            int limit,
            String reason,
            Instant coveredThrough
    ) {
        VariableHistoryService.VariableHistoryAggregateResponse raw = variableHistoryService.aggregate(
                objectPath, variableName, fieldName, from, to, bucketSpec, limit
        );
        return new IngestRollupReportResponse(
                raw.objectPath(),
                raw.variableName(),
                raw.field(),
                raw.bucket(),
                AXIS_OBSERVED_AT,
                SOURCE_RAW,
                coveredThrough,
                reason,
                raw.buckets()
        );
    }

    static Optional<Integer> materializedGrainSeconds(String bucketSpec) {
        if (bucketSpec == null || bucketSpec.isBlank()) {
            return Optional.empty();
        }
        return switch (bucketSpec.trim().toLowerCase(Locale.ROOT)) {
            case "1h" -> Optional.of(3_600);
            case "1d" -> Optional.of(86_400);
            default -> Optional.empty();
        };
    }

    private static String normalizeBucket(String bucketSpec) {
        return bucketSpec.trim().toLowerCase(Locale.ROOT);
    }

    public record IngestRollupReportResponse(
            String objectPath,
            String variableName,
            String field,
            String bucket,
            String axis,
            String source,
            Instant coveredThrough,
            String fallbackReason,
            List<VariableHistoryService.VariableHistoryBucket> buckets
    ) {
    }
}
