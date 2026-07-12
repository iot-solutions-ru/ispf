package com.ispf.analytics.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalyticsDagBuilderTest {

    @Test
    void rejectsCycle() {
        AnalyticsTagDefinition a = tag("root.a", "root.b");
        AnalyticsTagDefinition b = tag("root.b", "root.a");

        assertThatThrownBy(() -> AnalyticsDagBuilder.build(List.of(a, b)))
                .isInstanceOf(AnalyticsDagCycleException.class);
    }

    @Test
    void ordersChainAToBToC() {
        AnalyticsTagDefinition a = tag("root.a", "root.sensor");
        AnalyticsTagDefinition b = tag("root.b", "root.a");
        AnalyticsTagDefinition c = tag("root.c", "root.b");

        AnalyticsDag dag = AnalyticsDagBuilder.build(List.of(c, a, b));

        assertThat(dag.orderedTags()).extracting(AnalyticsTagDefinition::tagPath)
                .containsExactly("root.a", "root.b", "root.c");
    }

    @Test
    void adjacencyExposesDownstreamImpact() {
        AnalyticsTagDefinition a = tag("root.a", "root.sensor");
        AnalyticsTagDefinition b = tag("root.b", "root.a");
        AnalyticsTagDefinition c = tag("root.c", "root.b");

        AnalyticsDagBuilder.AnalyticsTagAdjacency adjacency = AnalyticsDagBuilder.adjacency(List.of(c, a, b));

        assertThat(adjacency.downstreamByTag().get("root.a")).containsExactly("root.b");
        assertThat(adjacency.downstreamByTag().get("root.b")).containsExactly("root.c");
        assertThat(adjacency.upstreamByTag().get("root.c")).containsExactly("root.b");
    }

    private static AnalyticsTagDefinition tag(String path, String sourcePath) {
        return new AnalyticsTagDefinition(
                path,
                "avg",
                List.of(new AnalyticsSourceRef(sourcePath, "derivedValue", "value")),
                "5m",
                60_000L,
                true,
                true,
                "derivedValue"
        );
    }
}
