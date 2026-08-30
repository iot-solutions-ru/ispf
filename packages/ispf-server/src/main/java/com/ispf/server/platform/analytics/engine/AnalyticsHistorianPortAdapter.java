package com.ispf.server.platform.analytics.engine;

import com.ispf.analytics.engine.HistorianPort;
import com.ispf.server.history.VariableHistoryService;
import com.ispf.server.security.acl.VariableAclRequestContext;
import com.ispf.server.security.acl.VariableMemberAccessService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class AnalyticsHistorianPortAdapter implements HistorianPort {

    private final VariableHistoryService variableHistoryService;
    private final VariableMemberAccessService variableMemberAccessService;

    AnalyticsHistorianPortAdapter(
            VariableHistoryService variableHistoryService,
            VariableMemberAccessService variableMemberAccessService
    ) {
        this.variableHistoryService = variableHistoryService;
        this.variableMemberAccessService = variableMemberAccessService;
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
        if (VariableAclRequestContext.isMemberEnforced()) {
            variableMemberAccessService.requireRead(
                    objectPath,
                    variableName,
                    VariableAclRequestContext.requireAuthentication()
            );
        }
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
        if (VariableAclRequestContext.isMemberEnforced()) {
            variableMemberAccessService.requireRead(
                    objectPath,
                    variableName,
                    VariableAclRequestContext.requireAuthentication()
            );
        }
        return variableHistoryService.query(objectPath, variableName, fieldName, from, to, limit)
                .samples()
                .stream()
                .map(sample -> new HistorianSample(sample.ts(), sample.value(), sample.text()))
                .toList();
    }
}
