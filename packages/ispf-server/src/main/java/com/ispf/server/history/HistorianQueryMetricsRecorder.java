package com.ispf.server.history;

import com.ispf.server.config.VariableHistorySloProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rolling historian query latency samples for SLA dashboards and lab gates (BL-161).
 */
@Service
public class HistorianQueryMetricsRecorder {

    public enum QueryKind {
        RAW("raw"),
        AGGREGATE("aggregate");

        private final String tag;

        QueryKind(String tag) {
            this.tag = tag;
        }

        String tag() {
            return tag;
        }
    }

    private static final int MAX_SAMPLES = 10_000;

    private final Optional<MeterRegistry> meterRegistry;
    private final ConcurrentLinkedDeque<Long> rawLatenciesMs = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<Long> aggregateLatenciesMs = new ConcurrentLinkedDeque<>();
    private final AtomicLong rawQueryCount = new AtomicLong();
    private final AtomicLong aggregateQueryCount = new AtomicLong();
    private final Optional<Timer> rawTimer;
    private final Optional<Timer> aggregateTimer;

    public HistorianQueryMetricsRecorder(Optional<MeterRegistry> meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.rawTimer = meterRegistry.map(registry -> Timer.builder("ispf.historian.query.latency")
                .tag("query", QueryKind.RAW.tag())
                .publishPercentiles(0.5, 0.95)
                .register(registry));
        this.aggregateTimer = meterRegistry.map(registry -> Timer.builder("ispf.historian.query.latency")
                .tag("query", QueryKind.AGGREGATE.tag())
                .publishPercentiles(0.5, 0.95)
                .register(registry));
    }

    public void recordRawQuery(long latencyMs) {
        record(QueryKind.RAW, latencyMs, rawLatenciesMs, rawQueryCount, rawTimer);
    }

    public void recordAggregateQuery(long latencyMs) {
        record(QueryKind.AGGREGATE, latencyMs, aggregateLatenciesMs, aggregateQueryCount, aggregateTimer);
    }

    public HistorianQuerySlaSnapshot snapshot(VariableHistorySloProperties sloProperties) {
        return new HistorianQuerySlaSnapshot(
                queryMetrics(QueryKind.RAW, rawLatenciesMs, rawQueryCount.get(), sloProperties),
                queryMetrics(
                        QueryKind.AGGREGATE,
                        aggregateLatenciesMs,
                        aggregateQueryCount.get(),
                        sloProperties
                )
        );
    }

    private void record(
            QueryKind kind,
            long latencyMs,
            ConcurrentLinkedDeque<Long> samples,
            AtomicLong counter,
            Optional<Timer> timer
    ) {
        long normalized = Math.max(0, latencyMs);
        counter.incrementAndGet();
        samples.addLast(normalized);
        trim(samples);
        timer.ifPresent(t -> t.record(java.time.Duration.ofMillis(normalized)));
    }

    private static void trim(ConcurrentLinkedDeque<Long> samples) {
        while (samples.size() > MAX_SAMPLES) {
            samples.pollFirst();
        }
    }

    private static QueryMetrics queryMetrics(
            QueryKind kind,
            ConcurrentLinkedDeque<Long> samples,
            long totalCount,
            VariableHistorySloProperties sloProperties
    ) {
        List<Long> sorted = new ArrayList<>(samples);
        Collections.sort(sorted);
        long sloMaxLatencyMs;
        long sloMaxPoints;
        if (kind == QueryKind.AGGREGATE) {
            sloMaxLatencyMs = sloProperties.getAggregateMaxLatencyMs();
            sloMaxPoints = sloProperties.getAggregateMaxPoints();
        } else {
            sloMaxLatencyMs = sloProperties.getRawQueryMaxLatencyMs();
            sloMaxPoints = sloProperties.getRawQueryMaxPoints();
        }
        return new QueryMetrics(
                kind.tag(),
                totalCount,
                sorted.size(),
                percentile(sorted, 0.50),
                percentile(sorted, 0.95),
                sloMaxLatencyMs,
                sloMaxPoints
        );
    }

    static long percentile(List<Long> sorted, double quantile) {
        if (sorted.isEmpty()) {
            return 0L;
        }
        int index = (int) Math.ceil(quantile * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }

    public record QueryMetrics(
            String query,
            long totalQueries,
            long windowSamples,
            long p50LatencyMs,
            long p95LatencyMs,
            long sloMaxLatencyMs,
            long sloMaxPoints
    ) {
        public boolean sloMet() {
            return windowSamples == 0 || p95LatencyMs <= sloMaxLatencyMs;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("query", query);
            map.put("totalQueries", totalQueries);
            map.put("windowSamples", windowSamples);
            map.put("p50LatencyMs", p50LatencyMs);
            map.put("p95LatencyMs", p95LatencyMs);
            map.put("sloMaxLatencyMs", sloMaxLatencyMs);
            map.put("sloMaxPoints", sloMaxPoints);
            map.put("sloMet", sloMet());
            return map;
        }
    }

    public record HistorianQuerySlaSnapshot(QueryMetrics raw, QueryMetrics aggregate) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("raw", raw.toMap());
            map.put("aggregate", aggregate.toMap());
            return map;
        }
    }
}
