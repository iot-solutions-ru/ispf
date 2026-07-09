package com.ispf.server.platform.analytics.catalog;

import com.ispf.analytics.engine.AnalyticsDagBuilder;
import com.ispf.analytics.engine.AnalyticsSourceRef;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds lineage graphs and downstream impact sets for analytics tags (BL-209).
 */
@Service
public class AnalyticsTagLineageService {

    private final ObjectManager objectManager;

    public AnalyticsTagLineageService(ObjectManager objectManager) {
        this.objectManager = objectManager;
    }

    public List<String> downstreamTagPaths(List<AnalyticsTagDefinition> tags, String tagPath) {
        if (tags.isEmpty()) {
            return List.of();
        }
        AnalyticsDagBuilder.AnalyticsTagAdjacency adjacency = AnalyticsDagBuilder.adjacency(tags);
        return List.copyOf(adjacency.downstreamByTag().getOrDefault(tagPath, Set.of()));
    }

    public List<String> upstreamTagPaths(List<AnalyticsTagDefinition> tags, String tagPath) {
        if (tags.isEmpty()) {
            return List.of();
        }
        AnalyticsDagBuilder.AnalyticsTagAdjacency adjacency = AnalyticsDagBuilder.adjacency(tags);
        return List.copyOf(adjacency.upstreamByTag().getOrDefault(tagPath, Set.of()));
    }

    public AnalyticsTagLineageGraph lineageForTag(List<AnalyticsTagDefinition> tags, String tagPath) {
        if (tags.isEmpty()) {
            return AnalyticsTagLineageGraph.empty();
        }
        AnalyticsDagBuilder.AnalyticsTagAdjacency adjacency = AnalyticsDagBuilder.adjacency(tags);
        Set<String> related = new LinkedHashSet<>();
        collectUpstream(adjacency.upstreamByTag(), tagPath, related);
        collectDownstream(adjacency.downstreamByTag(), tagPath, related);
        related.add(tagPath);

        Map<String, AnalyticsTagDefinition> byPath = new LinkedHashMap<>();
        for (AnalyticsTagDefinition tag : tags) {
            byPath.put(tag.tagPath(), tag);
        }

        List<AnalyticsTagLineageNode> nodes = new ArrayList<>();
        List<AnalyticsTagLineageEdge> edges = new ArrayList<>();
        for (String path : related) {
            AnalyticsTagDefinition tag = byPath.get(path);
            if (tag == null) {
                continue;
            }
            String label = objectManager.tree().findByPath(path)
                    .map(PlatformObject::displayName)
                    .filter(name -> !name.isBlank())
                    .orElse(path);
            nodes.add(new AnalyticsTagLineageNode(path, "tag", label, path, tag.outputVariable()));
            for (AnalyticsSourceRef source : tag.sources()) {
                String sourceId = sourceNodeId(source);
                if (nodes.stream().noneMatch(node -> node.id().equals(sourceId))) {
                    String sourceLabel = objectManager.tree().findByPath(source.path())
                            .map(PlatformObject::displayName)
                            .filter(name -> !name.isBlank())
                            .orElse(source.path());
                    nodes.add(new AnalyticsTagLineageNode(
                            sourceId,
                            "source",
                            sourceLabel + "." + source.variable(),
                            source.path(),
                            source.variable()
                    ));
                }
                if ("tag".equals(nodeKindForSource(tags, source))) {
                    edges.add(new AnalyticsTagLineageEdge(source.path(), path, "derived-from"));
                } else {
                    edges.add(new AnalyticsTagLineageEdge(sourceId, path, "reads"));
                }
            }
        }
        return new AnalyticsTagLineageGraph(List.copyOf(nodes), List.copyOf(edges));
    }

    private static String nodeKindForSource(List<AnalyticsTagDefinition> tags, AnalyticsSourceRef source) {
        boolean tagSource = tags.stream().anyMatch(tag ->
                tag.tagPath().equals(source.path())
                        && tag.outputVariable().equals(source.variable()));
        return tagSource ? "tag" : "source";
    }

    private static String sourceNodeId(AnalyticsSourceRef source) {
        return source.path() + "#" + source.variable() + "#" + source.field();
    }

    private static void collectUpstream(
            Map<String, Set<String>> upstreamByTag,
            String tagPath,
            Set<String> collected
    ) {
        for (String upstream : upstreamByTag.getOrDefault(tagPath, Set.of())) {
            if (collected.add(upstream)) {
                collectUpstream(upstreamByTag, upstream, collected);
            }
        }
    }

    private static void collectDownstream(
            Map<String, Set<String>> downstreamByTag,
            String tagPath,
            Set<String> collected
    ) {
        for (String downstream : downstreamByTag.getOrDefault(tagPath, Set.of())) {
            if (collected.add(downstream)) {
                collectDownstream(downstreamByTag, downstream, collected);
            }
        }
    }
}
