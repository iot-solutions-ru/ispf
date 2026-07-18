package com.ispf.server.platform.analytics;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic analysis helpers for analytics AI (ADR-0049 Wave 2B).
 */
@Service
public class AnalyticsAnalysisService {

    public Map<String, Object> analyzeSeries(List<Double> values, double anomalyZThreshold) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (values == null || values.isEmpty()) {
            result.put("count", 0);
            result.put("qualitySummary", Map.of("goodPct", 0.0, "gapCount", 0));
            return result;
        }
        List<Double> clean = values.stream().filter(v -> v != null && !v.isNaN() && !v.isInfinite()).toList();
        int gapCount = values.size() - clean.size();
        DoubleSummaryStatistics stats = clean.stream().mapToDouble(Double::doubleValue).summaryStatistics();
        double mean = stats.getAverage();
        double variance = 0.0;
        for (double v : clean) {
            double d = v - mean;
            variance += d * d;
        }
        double stddev = clean.size() > 1 ? Math.sqrt(variance / (clean.size() - 1)) : 0.0;
        double slope = trendSlope(clean);
        List<Double> zScores = new ArrayList<>();
        List<Map<String, Object>> outliers = new ArrayList<>();
        for (int i = 0; i < clean.size(); i++) {
            double z = stddev == 0.0 ? 0.0 : (clean.get(i) - mean) / stddev;
            zScores.add(z);
            double anomaly = Math.abs(z) + (gapCount > 0 ? 0.1 : 0.0);
            if (Math.abs(z) >= anomalyZThreshold) {
                Map<String, Object> outlier = new HashMap<>();
                outlier.put("index", i);
                outlier.put("value", clean.get(i));
                outlier.put("zScore", z);
                outlier.put("anomalyScore", anomaly);
                outliers.add(outlier);
            }
        }
        result.put("count", clean.size());
        result.put("min", stats.getMin());
        result.put("max", stats.getMax());
        result.put("avg", mean);
        result.put("rollingStddev", stddev);
        result.put("trendSlope", slope);
        result.put("percentile", Map.of(
                "p50", percentile(clean, 0.50),
                "p95", percentile(clean, 0.95),
                "p99", percentile(clean, 0.99)
        ));
        result.put("zScores", zScores);
        result.put("outliers", outliers);
        result.put("anomalyScore", outliers.isEmpty() ? 0.0 : outliers.stream()
                .mapToDouble(o -> ((Number) o.get("anomalyScore")).doubleValue())
                .max()
                .orElse(0.0));
        result.put("qualitySummary", Map.of(
                "goodPct", clean.isEmpty() ? 0.0 : (100.0 * clean.size() / values.size()),
                "gapCount", gapCount,
                "sampleCount", values.size()
        ));
        return result;
    }

    public Map<String, Object> periodOverPeriod(List<Double> current, List<Double> previous) {
        Map<String, Object> cur = analyzeSeries(current, 3.0);
        Map<String, Object> prev = analyzeSeries(previous, 3.0);
        double curAvg = ((Number) cur.getOrDefault("avg", 0.0)).doubleValue();
        double prevAvg = ((Number) prev.getOrDefault("avg", 0.0)).doubleValue();
        double delta = curAvg - prevAvg;
        double pct = prevAvg == 0.0 ? 0.0 : (100.0 * delta / prevAvg);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("current", cur);
        result.put("previous", prev);
        result.put("deltaAvg", delta);
        result.put("pctChange", pct);
        return result;
    }

    public static double trendSlope(List<Double> values) {
        int n = values.size();
        if (n < 2) {
            return 0.0;
        }
        double sumX = 0;
        double sumY = 0;
        double sumXY = 0;
        double sumXX = 0;
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = values.get(i);
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumXX += x * x;
        }
        double denom = n * sumXX - sumX * sumX;
        if (denom == 0.0) {
            return 0.0;
        }
        return (n * sumXY - sumX * sumY) / denom;
    }

    public static double percentile(List<Double> sortedSource, double p) {
        if (sortedSource.isEmpty()) {
            return 0.0;
        }
        List<Double> sorted = new ArrayList<>(sortedSource);
        sorted.sort(Double::compareTo);
        if (p <= 0) {
            return sorted.getFirst();
        }
        if (p >= 1) {
            return sorted.getLast();
        }
        double idx = p * (sorted.size() - 1);
        int lo = (int) Math.floor(idx);
        int hi = (int) Math.ceil(idx);
        if (lo == hi) {
            return sorted.get(lo);
        }
        double w = idx - lo;
        return sorted.get(lo) * (1 - w) + sorted.get(hi) * w;
    }
}
