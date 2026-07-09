package com.ispf.server.platform.analytics;

import com.ispf.server.config.AnalyticsProperties;
import com.ispf.server.history.VariableHistoryService;
import com.ispf.server.platform.analytics.frames.EventFrameService;
import com.ispf.server.platform.analytics.frames.EventFrameType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.UUID;

/**
 * Executes aligned multi-tag historian aggregate queries (BL-206).
 */
@Service
public class AnalyticsQueryService {

    private final VariableHistoryService variableHistoryService;
    private final AnalyticsProperties analyticsProperties;
    private final AnalyticsQueryRateLimiter rateLimiter;
    private final EventFrameService eventFrameService;
    private final ExecutorService executor = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "analytics-query-");
        thread.setDaemon(true);
        return thread;
    });

    public AnalyticsQueryService(
            VariableHistoryService variableHistoryService,
            AnalyticsProperties analyticsProperties,
            AnalyticsQueryRateLimiter rateLimiter,
            EventFrameService eventFrameService
    ) {
        this.variableHistoryService = variableHistoryService;
        this.analyticsProperties = analyticsProperties;
        this.rateLimiter = rateLimiter;
        this.eventFrameService = eventFrameService;
    }

    @Transactional(readOnly = true)
    public AnalyticsQueryResponse query(AnalyticsQueryRequest request) {
        rateLimiter.acquire();
        validate(request);

        long started = System.nanoTime();
        UUID frameId = request.frameId();
        EventFrameType frameType = null;
        Instant queryFrom = request.from();
        Instant queryTo = request.to();
        if (frameId != null) {
            EventFrameService.EventFrameWindow window = eventFrameService.resolveQueryWindow(frameId, queryFrom, queryTo);
            queryFrom = window.from();
            queryTo = window.to();
            frameType = window.frameType();
        }
        final Instant from = queryFrom;
        final Instant to = queryTo;
        int maxBuckets = request.maxBuckets() != null
                ? Math.min(Math.max(request.maxBuckets(), 1), 2_000)
                : 500;
        String agg = normalizeAgg(request.agg());

        List<CompletableFuture<TagSeriesResult>> futures = request.tags().stream()
                .map(tag -> CompletableFuture.supplyAsync(
                        () -> queryTag(tag, from, to, request.bucket(), maxBuckets, agg),
                        executor
                ))
                .toList();

        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        try {
            all.get(analyticsProperties.queryTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            futures.forEach(future -> future.cancel(true));
            throw new IllegalStateException(
                    "Analytics query timed out after " + analyticsProperties.queryTimeoutMs() + "ms"
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Analytics query interrupted", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Analytics query failed", cause);
        }

        List<TagSeriesResult> tagResults = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        Set<Instant> timestamps = new LinkedHashSet<>();
        for (TagSeriesResult result : tagResults) {
            timestamps.addAll(result.valuesByTs().keySet());
        }
        List<Instant> ordered = new ArrayList<>(timestamps);
        ordered.sort(Instant::compareTo);

        List<String> timestampStrings = ordered.stream().map(Instant::toString).toList();
        List<AnalyticsQueryResponse.AnalyticsQuerySeries> series = tagResults.stream()
                .map(result -> new AnalyticsQueryResponse.AnalyticsQuerySeries(
                        result.tag().seriesId(),
                        result.tag().path(),
                        result.tag().variable(),
                        result.tag().field(),
                        result.dataSource(),
                        ordered.stream()
                                .map(ts -> result.valuesByTs().get(ts))
                                .toList()
                ))
                .toList();

        long latencyMs = (System.nanoTime() - started) / 1_000_000L;
        return new AnalyticsQueryResponse(
                request.bucket(),
                from,
                to,
                agg,
                timestampStrings,
                series,
                latencyMs,
                frameId,
                frameType != null ? frameType.externalName() : null
        );
    }

    private TagSeriesResult queryTag(
            AnalyticsQueryRequest.AnalyticsQueryTag tag,
            Instant from,
            Instant to,
            String bucket,
            int maxBuckets,
            String agg
    ) {
        VariableHistoryService.VariableHistoryAggregateResponse aggregate = variableHistoryService.aggregate(
                tag.path(),
                tag.variable(),
                tag.field(),
                from,
                to,
                bucket,
                maxBuckets
        );
        Map<Instant, Double> valuesByTs = new LinkedHashMap<>();
        for (VariableHistoryService.VariableHistoryBucket bucketRow : aggregate.buckets()) {
            Double value = pickValue(bucketRow, agg);
            if (value != null && bucketRow.ts() != null) {
                valuesByTs.put(bucketRow.ts(), value);
            }
        }
        String dataSource = aggregate.dataSource() != null ? aggregate.dataSource() : "raw";
        return new TagSeriesResult(tag, dataSource, valuesByTs);
    }

    private static Double pickValue(VariableHistoryService.VariableHistoryBucket bucket, String agg) {
        return switch (agg) {
            case "min" -> bucket.min();
            case "max" -> bucket.max();
            case "last" -> bucket.avg();
            default -> bucket.avg();
        };
    }

    private void validate(AnalyticsQueryRequest request) {
        if (request.tags().isEmpty()) {
            throw new IllegalArgumentException("At least one tag is required");
        }
        if (request.tags().size() > analyticsProperties.queryMaxTags()) {
            throw new IllegalArgumentException(
                    "Too many tags (max " + analyticsProperties.queryMaxTags() + ")"
            );
        }
        if (request.from().isAfter(request.to())) {
            throw new IllegalArgumentException("from must be before to");
        }
        VariableHistoryService.parseBucket(request.bucket());
        normalizeAgg(request.agg());
    }

    private static String normalizeAgg(String agg) {
        String normalized = agg.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("avg", "min", "max", "last").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported agg: " + agg);
        }
        return normalized;
    }

    private record TagSeriesResult(
            AnalyticsQueryRequest.AnalyticsQueryTag tag,
            String dataSource,
            Map<Instant, Double> valuesByTs
    ) {
    }
}
