package com.ispf.analytics.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a dependency DAG from analytics tag definitions (BL-203).
 */
public final class AnalyticsDagBuilder {

    private static final Set<String> DERIVED_OUTPUT_VARIABLES = Set.of(
            "derivedValue",
            "oeePct",
            "availabilityPct",
            "performancePct",
            "qualityPct"
    );

    public record AnalyticsTagAdjacency(
            Map<String, Set<String>> downstreamByTag,
            Map<String, Set<String>> upstreamByTag
    ) {
    }

    private AnalyticsDagBuilder() {
    }

    public static AnalyticsTagAdjacency adjacency(List<AnalyticsTagDefinition> tags) {
        AdjacencyMaps maps = buildAdjacencyMaps(tags);
        return new AnalyticsTagAdjacency(
                copyAdjacency(maps.downstream()),
                copyAdjacency(maps.upstream())
        );
    }

    public static AnalyticsDag build(List<AnalyticsTagDefinition> tags) {
        if (tags.isEmpty()) {
            return new AnalyticsDag(List.of());
        }
        Map<String, AnalyticsTagDefinition> byPath = indexTags(tags);
        AdjacencyMaps maps = buildAdjacencyMaps(tags, byPath);
        detectCycles(maps.downstream(), byPath.keySet());
        return new AnalyticsDag(topologicalOrder(byPath, maps.downstream(), maps.indegree()));
    }

    private static Map<String, AnalyticsTagDefinition> indexTags(List<AnalyticsTagDefinition> tags) {
        Map<String, AnalyticsTagDefinition> byPath = new LinkedHashMap<>();
        for (AnalyticsTagDefinition tag : tags) {
            if (byPath.put(tag.tagPath(), tag) != null) {
                throw new IllegalArgumentException("Duplicate analytics tag path: " + tag.tagPath());
            }
        }
        return byPath;
    }

    private static AdjacencyMaps buildAdjacencyMaps(List<AnalyticsTagDefinition> tags) {
        return buildAdjacencyMaps(tags, indexTags(tags));
    }

    private static AdjacencyMaps buildAdjacencyMaps(
            List<AnalyticsTagDefinition> tags,
            Map<String, AnalyticsTagDefinition> byPath
    ) {
        Map<String, Set<String>> downstream = new HashMap<>();
        Map<String, Set<String>> upstream = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        for (String path : byPath.keySet()) {
            downstream.put(path, new HashSet<>());
            upstream.put(path, new HashSet<>());
            indegree.put(path, 0);
        }

        for (AnalyticsTagDefinition tag : tags) {
            for (AnalyticsSourceRef source : tag.sources()) {
                if (!byPath.containsKey(source.path())) {
                    continue;
                }
                if (!DERIVED_OUTPUT_VARIABLES.contains(source.variable())) {
                    continue;
                }
                if (source.path().equals(tag.tagPath())) {
                    throw new AnalyticsDagCycleException("Self-referencing analytics tag: " + tag.tagPath());
                }
                if (downstream.get(source.path()).add(tag.tagPath())) {
                    upstream.get(tag.tagPath()).add(source.path());
                    indegree.merge(tag.tagPath(), 1, Integer::sum);
                }
            }
        }
        return new AdjacencyMaps(downstream, upstream, indegree);
    }

    private static List<AnalyticsTagDefinition> topologicalOrder(
            Map<String, AnalyticsTagDefinition> byPath,
            Map<String, Set<String>> downstream,
            Map<String, Integer> indegree
    ) {
        Deque<String> ready = new ArrayDeque<>();
        Map<String, Integer> remaining = new HashMap<>(indegree);
        for (Map.Entry<String, Integer> entry : remaining.entrySet()) {
            if (entry.getValue() == 0) {
                ready.add(entry.getKey());
            }
        }

        List<AnalyticsTagDefinition> ordered = new ArrayList<>(byPath.size());
        while (!ready.isEmpty()) {
            String path = ready.removeFirst();
            ordered.add(byPath.get(path));
            for (String dependent : downstream.get(path)) {
                int next = remaining.merge(dependent, -1, Integer::sum);
                if (next == 0) {
                    ready.add(dependent);
                }
            }
        }

        if (ordered.size() != byPath.size()) {
            throw new AnalyticsDagCycleException("Analytics tag dependency cycle detected");
        }
        return ordered;
    }

    private static Map<String, Set<String>> copyAdjacency(Map<String, Set<String>> source) {
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : source.entrySet()) {
            copy.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    private record AdjacencyMaps(
            Map<String, Set<String>> downstream,
            Map<String, Set<String>> upstream,
            Map<String, Integer> indegree
    ) {
    }

    private static void detectCycles(Map<String, Set<String>> adjacency, Set<String> nodes) {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String node : nodes) {
            if (visited.contains(node)) {
                continue;
            }
            if (dfsCycle(node, adjacency, visiting, visited)) {
                throw new AnalyticsDagCycleException("Analytics tag dependency cycle detected at " + node);
            }
        }
    }

    private static boolean dfsCycle(
            String node,
            Map<String, Set<String>> adjacency,
            Set<String> visiting,
            Set<String> visited
    ) {
        if (!visiting.add(node)) {
            return true;
        }
        for (String next : adjacency.getOrDefault(node, Set.of())) {
            if (visited.contains(next)) {
                continue;
            }
            if (dfsCycle(next, adjacency, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(node);
        visited.add(node);
        return false;
    }
}
