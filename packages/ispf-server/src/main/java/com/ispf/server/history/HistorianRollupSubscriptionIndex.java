package com.ispf.server.history;

import com.ispf.analytics.engine.AnalyticsSourceRef;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.server.platform.analytics.engine.AnalyticsTagCatalogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves rollup materialization subscriptions from the analytics catalog (BL-205).
 */
@Service
public class HistorianRollupSubscriptionIndex {

    private final AnalyticsTagCatalogService catalogService;

    public HistorianRollupSubscriptionIndex(AnalyticsTagCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @Transactional(readOnly = true)
    public List<HistorianRollupSubscription> listSubscriptions() {
        Map<String, HistorianRollupSubscription> unique = new LinkedHashMap<>();
        for (AnalyticsTagDefinition tag : catalogService.listEnabledTags()) {
            for (AnalyticsSourceRef source : tag.sources()) {
                for (String bucketSpec : tag.rollupBuckets()) {
                    Duration bucket = VariableHistoryService.parseBucket(bucketSpec);
                    HistorianRollupSubscription subscription = new HistorianRollupSubscription(
                            source.path(),
                            source.variable(),
                            source.field(),
                            bucket
                    );
                    unique.put(subscriptionKey(subscription), subscription);
                }
            }
        }
        return List.copyOf(unique.values());
    }

    @Transactional(readOnly = true)
    public boolean isSubscribed(String objectPath, String variableName, String fieldName, Duration bucket) {
        HistorianRollupSubscription probe = new HistorianRollupSubscription(
                objectPath,
                variableName,
                fieldName,
                bucket
        );
        return listSubscriptions().stream().anyMatch(s -> subscriptionKey(s).equals(subscriptionKey(probe)));
    }

    @Transactional(readOnly = true)
    public List<HistorianRollupSubscription> subscriptionsFor(String objectPath, String variableName, String fieldName) {
        List<HistorianRollupSubscription> matches = new ArrayList<>();
        for (HistorianRollupSubscription subscription : listSubscriptions()) {
            if (subscription.objectPath().equals(objectPath)
                    && subscription.variableName().equals(variableName)
                    && subscription.fieldName().equals(fieldName)) {
                matches.add(subscription);
            }
        }
        return matches;
    }

    private static String subscriptionKey(HistorianRollupSubscription subscription) {
        return subscription.objectPath()
                + "|" + subscription.variableName()
                + "|" + subscription.fieldName()
                + "|" + subscription.bucketWidthSec();
    }
}
