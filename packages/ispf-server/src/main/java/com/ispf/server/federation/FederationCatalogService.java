package com.ispf.server.federation;

import tools.jackson.databind.JsonNode;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.bootstrap.SystemObjectDescriptions;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    @Transactional(readOnly = true)
    public CatalogSyncPreview previewCatalogSync(UUID peerId) {
        FederationPeer peer = requirePeer(peerId);
        String localRoot = FederationPaths.peerCatalogRoot(peer.name());
        List<RemoteEntry> entries = collectRemoteEntries(peer);
        List<CatalogSyncConflict> conflicts = new ArrayList<>();
        int createCount = 0;
        int updateCount = 0;
        for (RemoteEntry entry : entries) {
            Optional<CatalogSyncConflict> conflict = detectConflict(entry, peerId);
            if (conflict.isPresent()) {
                conflicts.add(conflict.get());
                continue;
            }
            if (objectManager.tree().findByPath(entry.localPath()).isPresent()) {
                updateCount++;
            } else {
                createCount++;
            }
        }
        return new CatalogSyncPreview(localRoot, entries.size(), createCount, updateCount, conflicts);
    }

    @Transactional
    public SyncResult syncCatalog(UUID peerId) {
        return syncCatalog(peerId, List.of());
    }

    @Transactional
    public SyncResult syncCatalog(UUID peerId, List<CatalogSyncResolution> resolutions) {
        FederationPeer peer = requirePeer(peerId);
        String localRoot = FederationPaths.peerCatalogRoot(peer.name());
        ensureCatalogRoot(localRoot, peer);
        List<RemoteEntry> entries = collectRemoteEntries(peer);
        Map<String, CatalogSyncResolutionAction> resolutionByPath = new HashMap<>();
        for (CatalogSyncResolution resolution : resolutions) {
            resolutionByPath.put(resolution.localPath(), resolution.action());
        }

        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (RemoteEntry entry : entries) {
            Optional<CatalogSyncConflict> conflict = detectConflict(entry, peerId);
            if (conflict.isPresent()) {
                CatalogSyncResolutionAction action = resolutionByPath.get(entry.localPath());
                if (action != CatalogSyncResolutionAction.BIND) {
                    skipped++;
                    continue;
                }
            }
            if (objectManager.tree().findByPath(entry.localPath()).isPresent()) {
                markProxy(entry.localPath(), peerId, entry.remotePath());
                updated++;
            } else {
                createProxyNode(entry.localPath(), entry.remote(), peerId, entry.remotePath());
                created++;
            }
        }
        return new SyncResult(localRoot, created, updated, entries.size(), skipped);
    }

    private List<RemoteEntry> collectRemoteEntries(FederationPeer peer) {
        JsonNode remoteObjects = federationService.proxyObjectList(peer.id());
        if (!remoteObjects.isArray()) {
            throw new IllegalStateException("Remote object list is not an array");
        }
        String localRoot = FederationPaths.peerCatalogRoot(peer.name());
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
        return entries;
    }

    private Optional<CatalogSyncConflict> detectConflict(RemoteEntry entry, UUID peerId) {
        Optional<PlatformObject> existing = objectManager.tree().findByPath(entry.localPath());
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        PlatformObject node = existing.get();
        if (!FederationProxyMetadata.isProxy(node)) {
            return Optional.of(new CatalogSyncConflict(
                    entry.localPath(),
                    entry.remotePath(),
                    CatalogSyncConflictType.LOCAL_NATIVE,
                    null,
                    null,
                    node.displayName(),
                    node.type().name()
            ));
        }
        UUID existingPeerId = FederationProxyMetadata.peerId(node).orElse(null);
        String existingRemotePath = FederationProxyMetadata.remotePath(node).orElse("");
        if (!peerId.equals(existingPeerId) || !entry.remotePath().equals(existingRemotePath)) {
            return Optional.of(new CatalogSyncConflict(
                    entry.localPath(),
                    entry.remotePath(),
                    CatalogSyncConflictType.PROXY_MISMATCH,
                    existingPeerId != null ? existingPeerId.toString() : null,
                    existingRemotePath,
                    node.displayName(),
                    node.type().name()
            ));
        }
        return Optional.empty();
    }

    private FederationPeer requirePeer(UUID peerId) {
        return peerStore.findById(peerId)
                .orElseThrow(() -> new IllegalArgumentException("Peer not found: " + peerId));
    }

    private record RemoteEntry(String remotePath, String localPath, JsonNode remote) {
    }

    private void ensureCatalogRoot(String localRoot, FederationPeer peer) {
        if (objectManager.tree().findByPath(localRoot).isPresent()) {
            SystemObjectDescriptions.resolve(localRoot).ifPresent(entry ->
                    objectManager.updateInfo(localRoot, entry.displayName(), entry.description())
            );
            return;
        }
        int lastDot = localRoot.lastIndexOf('.');
        String parentPath = localRoot.substring(0, lastDot);
        String name = localRoot.substring(lastDot + 1);
        if (objectManager.tree().findByPath(parentPath).isEmpty()) {
            throw new IllegalStateException("Missing federation parent: " + parentPath);
        }
        SystemObjectDescriptions.Entry entry = SystemObjectDescriptions.resolve(localRoot)
                .orElse(new SystemObjectDescriptions.Entry(
                        peer.name(),
                        "Federated catalog from " + peer.baseUrl()
                ));
        objectManager.create(
                parentPath,
                name,
                ObjectType.AGENT,
                entry.displayName(),
                entry.description(),
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

    public record CatalogSyncConflict(
            String localPath,
            String remotePath,
            CatalogSyncConflictType type,
            String existingPeerId,
            String existingRemotePath,
            String localDisplayName,
            String localType
    ) {
    }

    public record CatalogSyncPreview(
            String localRoot,
            int remoteCount,
            int createCount,
            int updateCount,
            List<CatalogSyncConflict> conflicts
    ) {
    }

    public record CatalogSyncResolution(String localPath, CatalogSyncResolutionAction action) {
    }

    public record SyncResult(String localRoot, int created, int updated, int remoteCount, int skipped) {
    }
}
