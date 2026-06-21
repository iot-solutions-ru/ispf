package com.ispf.server.federation;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.object.ObjectManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class FederationProxyService {

    private final ObjectManager objectManager;
    private final FederationService federationService;
    private final FederationPeerStore peerStore;
    private final ObjectMapper objectMapper;

    public FederationProxyService(
            ObjectManager objectManager,
            FederationService federationService,
            FederationPeerStore peerStore,
            ObjectMapper objectMapper
    ) {
        this.objectManager = objectManager;
        this.federationService = federationService;
        this.peerStore = peerStore;
        this.objectMapper = objectMapper;
    }

    public Optional<FederationProxyTarget> resolve(String localPath) {
        return objectManager.tree().findByPath(localPath)
                .flatMap(this::toTarget);
    }

    public Optional<FederationProxyTarget> toTarget(PlatformObject node) {
        if (!FederationProxyMetadata.isProxy(node)) {
            return Optional.empty();
        }
        Optional<UUID> peerId = FederationProxyMetadata.peerId(node);
        Optional<String> remotePath = FederationProxyMetadata.remotePath(node);
        if (peerId.isEmpty() || remotePath.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new FederationProxyTarget(localPath(node), peerId.get(), remotePath.get()));
    }

    public JsonNode proxyObject(FederationProxyTarget target) {
        return federationService.proxyObjectByPath(target.peerId(), target.remotePath());
    }

    public JsonNode proxyVariables(FederationProxyTarget target) {
        return federationService.proxyVariablesByPath(target.peerId(), target.remotePath());
    }

    public JsonNode proxyDashboard(FederationProxyTarget target) {
        FederationPeer peer = requirePeer(target.peerId());
        JsonNode json = federationService.proxyDashboardByPath(target.peerId(), target.remotePath());
        String remotePrefix = FederationPathRemapper.normalizePrefix(peer.pathPrefix());
        String localRoot = FederationPathRemapper.localCatalogRoot(
                target.localPath(),
                target.remotePath(),
                remotePrefix
        );
        ObjectNode copy = (ObjectNode) json.deepCopy();
        copy.put("path", target.localPath());
        if (copy.hasNonNull("layoutJson") && copy.get("layoutJson").isTextual()) {
            copy.put(
                    "layoutJson",
                    FederationPathRemapper.remapLayoutJson(
                            copy.get("layoutJson").asText(),
                            remotePrefix,
                            localRoot,
                            objectMapper
                    )
            );
        }
        if (copy.has("layout") && !copy.get("layout").isNull()) {
            Object remappedLayout = FederationPathRemapper.remapLayoutObject(
                    objectMapper.convertValue(copy.get("layout"), Object.class),
                    remotePrefix,
                    localRoot,
                    objectMapper
            );
            copy.set("layout", objectMapper.valueToTree(remappedLayout));
        }
        return copy;
    }

    public JsonNode proxyVariableHistory(
            FederationProxyTarget target,
            String name,
            String field,
            Instant from,
            Instant to,
            int limit
    ) {
        return federationService.proxyVariableHistory(
                target.peerId(),
                target.remotePath(),
                name,
                field,
                from,
                to,
                limit
        );
    }

    public JsonNode proxyVariableHistoryAggregate(
            FederationProxyTarget target,
            String name,
            String field,
            String bucket,
            Instant from,
            Instant to,
            int limit
    ) {
        return federationService.proxyVariableHistoryAggregate(
                target.peerId(),
                target.remotePath(),
                name,
                field,
                bucket,
                from,
                to,
                limit
        );
    }

    public JsonNode proxyVariablePut(FederationProxyTarget target, String variableName, String bodyJson) {
        JsonNode result = federationService.proxyVariablePut(
                target.peerId(),
                target.remotePath(),
                variableName,
                bodyJson
        );
        return result;
    }

    private FederationPeer requirePeer(UUID peerId) {
        return peerStore.findById(peerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Peer not found: " + peerId));
    }

    private static String localPath(PlatformObject node) {
        return node.path();
    }

    public record FederationProxyTarget(String localPath, UUID peerId, String remotePath) {
    }
}
