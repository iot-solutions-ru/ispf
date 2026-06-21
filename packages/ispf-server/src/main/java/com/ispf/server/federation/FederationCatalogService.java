package com.ispf.server.federation;

import tools.jackson.databind.JsonNode;
import com.ispf.core.object.ObjectType;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class FederationCatalogService {

    private final FederationPeerStore peerStore;
    private final FederationService federationService;
    private final ObjectManager objectManager;

    public FederationCatalogService(
            FederationPeerStore peerStore,
            FederationService federationService,
            ObjectManager objectManager
    ) {
        this.peerStore = peerStore;
        this.federationService = federationService;
        this.objectManager = objectManager;
    }

    @Transactional
    public SyncResult syncCatalog(UUID peerId) {
        FederationPeer peer = peerStore.findById(peerId)
                .orElseThrow(() -> new IllegalArgumentException("Peer not found: " + peerId));
        String localRoot = FederationPaths.peerCatalogRoot(peer.name());
        ensureCatalogRoot(localRoot, peer);
        JsonNode remoteObjects = federationService.proxyObjectList(peerId);
        if (!remoteObjects.isArray()) {
            throw new IllegalStateException("Remote object list is not an array");
        }

        int created = 0;
        int updated = 0;
        String prefix = normalizePrefix(peer.pathPrefix());
        List<RemoteEntry> entries = new ArrayList<>();
        for (JsonNode remote : remoteObjects) {
            String remotePath = textOrNull(remote, "path");
            if (remotePath == null || remotePath.isBlank()) {
                continue;
            }
            if (FederationPaths.isCatalogMirrorPath(remotePath)) {
                continue;
            }
            if (!remotePath.equals(prefix) && !remotePath.startsWith(prefix + ".")) {
                continue;
            }
            String suffix = remotePath.equals(prefix)
                    ? ""
                    : remotePath.substring(prefix.length());
            entries.add(new RemoteEntry(remotePath, localRoot + suffix, remote));
        }
        entries.sort(Comparator.comparingInt(entry -> entry.remotePath().length()));

        for (RemoteEntry entry : entries) {
            if (objectManager.tree().findByPath(entry.localPath()).isPresent()) {
                markProxy(entry.localPath(), peerId, entry.remotePath());
                updated++;
                continue;
            }
            createProxyNode(entry.localPath(), entry.remote(), peerId, entry.remotePath());
            created++;
        }
        return new SyncResult(localRoot, created, updated, remoteObjects.size());
    }

    private record RemoteEntry(String remotePath, String localPath, JsonNode remote) {
    }

    private void ensureCatalogRoot(String localRoot, FederationPeer peer) {
        if (objectManager.tree().findByPath(localRoot).isPresent()) {
            return;
        }
        int lastDot = localRoot.lastIndexOf('.');
        String parentPath = localRoot.substring(0, lastDot);
        String name = localRoot.substring(lastDot + 1);
        if (objectManager.tree().findByPath(parentPath).isEmpty()) {
            throw new IllegalStateException("Missing federation parent: " + parentPath);
        }
        objectManager.create(
                parentPath,
                name,
                ObjectType.AGENT,
                peer.name(),
                "Federated catalog from " + peer.baseUrl(),
                null
        );
    }

    private void createProxyNode(
            String localPath,
            JsonNode remote,
            UUID peerId,
            String remotePath
    ) {
        FederationProxyNodeHelper.createProxyNode(objectManager, localPath, remote, peerId, remotePath);
    }

    private void markProxy(String localPath, UUID peerId, String remotePath) {
        FederationProxyNodeHelper.markProxy(objectManager, localPath, peerId, remotePath);
    }

    private static String textOrNull(JsonNode node, String field) {
        return FederationProxyNodeHelper.textOrNull(node, field);
    }

    private static String textOrDefault(JsonNode node, String field, String defaultValue) {
        return FederationProxyNodeHelper.textOrDefault(node, field, defaultValue);
    }

    private static String normalizePrefix(String pathPrefix) {
        if (pathPrefix == null || pathPrefix.isBlank()) {
            return "root.platform";
        }
        String trimmed = pathPrefix.trim();
        while (trimmed.endsWith(".")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    public record SyncResult(String localRoot, int created, int updated, int remoteCount) {
    }
}
