package com.ispf.server.platform.analytics.engine;

import com.ispf.analytics.engine.HistorianPort;
import com.ispf.server.history.VariableHistoryService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class AnalyticsHistorianPortAdapter implements HistorianPort {

    private final VariableHistoryService variableHistoryService;

    AnalyticsHistorianPortAdapter(VariableHistoryService variableHistoryService) {
        this.variableHistoryService = variableHistoryService;
    }

    @Override
    public List<HistorianBucket> aggregate(
            String objectPath,
            String variableName,
            String fieldName,
            Instant from,
            Instant to,
            String windowBucket,
            int maxBuckets
    ) {
        return variableHistoryService.aggregate(objectPath, variableName, fieldName, from, to, windowBucket, maxBuckets)
                .buckets()
                .stream()
                .map(bucket -> new HistorianBucket(bucket.ts(), bucket.avg(), bucket.min(), bucket.max(), bucket.count()))
                .toList();
    }

    @Override
    public List<HistorianSample> query(
            String objectPath,
            String variableName,
            String fieldName,
            Instant from,
            Instant to,
            int limit
    ) {
        return variableHistoryService.query(objectPath, variableName, fieldName, from, to, limit)
                .samples()
                .stream()
                .map(sample -> new HistorianSample(sample.ts(), sample.value(), sample.text()))
                .toList();
    }
}
