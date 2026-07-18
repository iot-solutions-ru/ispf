package com.ispf.server.platform.analytics;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsAnalysisServiceTest {

    private final AnalyticsAnalysisService service = new AnalyticsAnalysisService();

    @Test
    void analyzeSeriesComputesStatsAndOutliers() {
        List<Double> values = List.of(10.0, 10.0, 10.0, 10.0, 100.0);
        Map<String, Object> result = service.analyzeSeries(values, 1.5);
        assertThat(result.get("count")).isEqualTo(5);
        assertThat(((Number) result.get("avg")).doubleValue()).isGreaterThan(10.0);
        assertThat((List<?>) result.get("outliers")).isNotEmpty();
        assertThat(((Number) result.get("anomalyScore")).doubleValue()).isGreaterThan(0.0);
    }

    @Test
    void periodOverPeriodReportsDelta() {
        Map<String, Object> result = service.periodOverPeriod(
                List.of(20.0, 22.0, 21.0),
                List.of(10.0, 11.0, 10.0)
        );
        assertThat(((Number) result.get("deltaAvg")).doubleValue()).isGreaterThan(0.0);
    }
}
