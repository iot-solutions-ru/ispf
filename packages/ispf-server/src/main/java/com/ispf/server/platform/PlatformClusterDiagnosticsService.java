package com.ispf.server.platform;

import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PlatformClusterDiagnosticsService {

    private static final Duration PEER_TIMEOUT = Duration.ofSeconds(2);

    private final ClusterReplicaRegistryService replicaRegistryService;
    private final PlatformDiagnosticsService diagnosticsService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(PEER_TIMEOUT)
            .build();

    public PlatformClusterDiagnosticsService(
            ClusterReplicaRegistryService replicaRegistryService,
            PlatformDiagnosticsService diagnosticsService,
            ObjectMapper objectMapper
    ) {
        this.replicaRegistryService = replicaRegistryService;
        this.diagnosticsService = diagnosticsService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> clusterDiagnostics(String authorizationHeader) {
        List<ClusterReplicaRegistryService.ClusterNode> nodes = replicaRegistryService.listNodes();
        List<Map<String, Object>> nodeRows = new ArrayList<>();
        for (ClusterReplicaRegistryService.ClusterNode node : nodes) {
            if (node.self()) {
                nodeRows.add(nodeDiagnostics(node, diagnosticsService.snapshot(), true, null));
                continue;
            }
            if (!"UP".equals(node.status()) || node.httpPort() == null || node.httpPort() <= 0) {
                nodeRows.add(unreachableNode(node, "Node not reachable (status or http_port missing)"));
                continue;
            }
            try {
                Map<String, Object> peerDiagnostics = fetchPeerDiagnostics(node.httpPort(), authorizationHeader);
                nodeRows.add(nodeDiagnostics(node, peerDiagnostics, true, null));
            } catch (Exception ex) {
                nodeRows.add(unreachableNode(node, ex.getMessage()));
            }
        }

        nodeRows.sort(Comparator.comparingDouble(row ->
                -((Number) row.getOrDefault("processCpuPercent", 0)).doubleValue()));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", Instant.now().toString());
        response.put("nodes", nodeRows);
        response.put("clusterTopSuspect", resolveClusterTopSuspect(nodeRows));
        return response;
    }

    private Map<String, Object> fetchPeerDiagnostics(int httpPort, String authorizationHeader) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + httpPort + "/api/v1/platform/metrics"))
                .timeout(PEER_TIMEOUT)
                .GET();
        if (authorizationHeader != null && !authorizationHeader.isBlank()) {
            builder.header("Authorization", authorizationHeader);
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        Map<String, Object> payload = objectMapper.readValue(response.body(), new TypeReference<>() {
        });
        Object diagnostics = payload.get("diagnostics");
        if (diagnostics instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) map;
            return cast;
        }
        throw new IllegalStateException("Peer response missing diagnostics");
    }

    private static Map<String, Object> nodeDiagnostics(
            ClusterReplicaRegistryService.ClusterNode node,
            Map<String, Object> diagnostics,
            boolean reachable,
            String error
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("replicaId", node.replicaId());
        row.put("replicaProfile", node.replicaProfile());
        row.put("status", node.status());
        row.put("httpPort", node.httpPort());
        row.put("self", node.self());
        row.put("reachable", reachable);
        if (error != null) {
            row.put("error", error);
        }
        row.put("processCpuPercent", diagnostics.getOrDefault("processCpuPercent", 0));
        row.put("systemCpuPercent", diagnostics.get("systemCpuPercent"));
        row.put("heapUsedPercent", diagnostics.get("heapUsedPercent"));
        row.put("pressureScore", diagnostics.getOrDefault("pressureScore", 0));
        row.put("topSuspect", diagnostics.get("topSuspect"));
        row.put("suspects", diagnostics.getOrDefault("suspects", List.of()));
        row.put("detail", diagnostics.getOrDefault("detail", Map.of()));
        row.put("queues", detailQueues(diagnostics));
        return row;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> detailQueues(Map<String, Object> diagnostics) {
        Object detail = diagnostics.get("detail");
        if (detail instanceof Map<?, ?> detailMap) {
            Object queues = detailMap.get("queues");
            if (queues instanceof Map<?, ?> queueMap) {
                return (Map<String, Object>) queueMap;
            }
        }
        return Map.of();
    }

    private static Map<String, Object> unreachableNode(
            ClusterReplicaRegistryService.ClusterNode node,
            String error
    ) {
        Map<String, Object> empty = Map.of(
                "processCpuPercent", 0,
                "pressureScore", 0,
                "suspects", List.of(),
                "detail", Map.of()
        );
        return nodeDiagnostics(node, empty, false, error);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resolveClusterTopSuspect(List<Map<String, Object>> nodeRows) {
        Map<String, Object> bestNode = null;
        Map<String, Object> bestSuspect = null;
        int bestScore = -1;

        for (Map<String, Object> node : nodeRows) {
            if (!Boolean.TRUE.equals(node.get("reachable"))) {
                continue;
            }
            Object suspectsObj = node.get("suspects");
            if (!(suspectsObj instanceof List<?> suspects) || suspects.isEmpty()) {
                double cpu = ((Number) node.getOrDefault("processCpuPercent", 0)).doubleValue();
                if (cpu >= 70 && cpu > bestScore) {
                    bestScore = (int) cpu;
                    bestNode = node;
                    bestSuspect = Map.of(
                            "kind", "subsystem",
                            "title", "Высокая загрузка CPU",
                            "detail", "processCpuPercent=" + cpu,
                            "ref", ""
                    );
                }
                continue;
            }
            Map<String, Object> top = (Map<String, Object>) suspects.getFirst();
            int score = ((Number) top.getOrDefault("score", 0)).intValue();
            if (score > bestScore) {
                bestScore = score;
                bestNode = node;
                bestSuspect = top;
            }
        }

        if (bestNode == null || bestSuspect == null) {
            return Map.of();
        }

        Map<String, Object> clusterTop = new LinkedHashMap<>();
        clusterTop.put("replicaId", bestNode.get("replicaId"));
        clusterTop.put("kind", bestSuspect.get("kind"));
        clusterTop.put("title", bestSuspect.get("title"));
        clusterTop.put("detail", bestSuspect.get("detail"));
        clusterTop.put("ref", bestSuspect.getOrDefault("ref", ""));
        return clusterTop;
    }
}
