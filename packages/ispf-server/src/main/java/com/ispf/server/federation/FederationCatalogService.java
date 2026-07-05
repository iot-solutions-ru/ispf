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
        return previewSubtreeSync(peerId, null, null);
    }

    @Transactional(readOnly = true)
    public CatalogSyncPreview previewSubtreeSync(UUID peerId, String remoteSubtreePath, String localParentPath) {
        FederationPeer peer = requirePeer(peerId);
        SubtreeScope scope = resolveSubtreeScope(peer, remoteSubtreePath, localParentPath);
        List<RemoteEntry> entries = collectRemoteEntries(peer, scope);
        return buildPreview(scope.localRoot(), entries, peerId);
    }

    @Transactional
    public SyncResult syncCatalog(UUID peerId) {
        return syncCatalog(peerId, List.of());
    }

    @Transactional
    public SyncResult syncCatalog(UUID peerId, List<CatalogSyncResolution> resolutions) {
        return syncSubtree(peerId, null, null, resolutions);
    }

    @Transactional
    public SyncResult syncSubtree(
            UUID peerId,
            String remoteSubtreePath,
            String localParentPath,
            List<CatalogSyncResolution> resolutions
    ) {
        FederationPeer peer = requirePeer(peerId);
        SubtreeScope scope = resolveSubtreeScope(peer, remoteSubtreePath, localParentPath);
        ensureCatalogRoot(FederationPaths.peerCatalogRoot(peer.name()), peer);
        if (!scope.localRoot().equals(FederationPaths.peerCatalogRoot(peer.name()))) {
            ensureSubtreeLocalRoot(scope.localRoot(), peer);
        }
        List<RemoteEntry> entries = collectRemoteEntries(peer, scope);
        return applySync(peerId, scope.localRoot(), entries, resolutions);
    }

    private CatalogSyncPreview buildPreview(String localRoot, List<RemoteEntry> entries, UUID peerId) {
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

    private SyncResult applySync(
            UUID peerId,
            String localRoot,
            List<RemoteEntry> entries,
            List<CatalogSyncResolution> resolutions
    ) {
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

    private SubtreeScope resolveSubtreeScope(
            FederationPeer peer,
            String remoteSubtreePath,
            String localParentPath
    ) {
        String peerPrefix = normalizePrefix(peer.pathPrefix());
        String remoteFilter = normalizePrefix(
                remoteSubtreePath == null || remoteSubtreePath.isBlank() ? peerPrefix : remoteSubtreePath.trim()
        );
        if (!remoteFilter.equals(peerPrefix) && !remoteFilter.startsWith(peerPrefix + ".")) {
            throw new IllegalArgumentException("remoteSubtreePath must be under peer pathPrefix: " + peerPrefix);
        }
        String catalogRoot = FederationPaths.peerCatalogRoot(peer.name());
        String suffix = remoteFilter.length() > peerPrefix.length()
                ? remoteFilter.substring(peerPrefix.length())
                : "";
        String defaultLocalRoot = catalogRoot + suffix;
        String localRoot = localParentPath == null || localParentPath.isBlank()
                ? defaultLocalRoot
                : localParentPath.trim();
        if (!FederationPaths.isCatalogMirrorPath(localRoot)) {
            throw new IllegalArgumentException("localParentPath must be under " + FederationPaths.FEDERATION_ROOT);
        }
        return new SubtreeScope(remoteFilter, localRoot);
    }

    private record SubtreeScope(String remoteFilter, String localRoot) {
    }

    private List<RemoteEntry> collectRemoteEntries(FederationPeer peer, SubtreeScope scope) {
        JsonNode remoteObjects = federationService.proxyObjectList(peer.id());
        if (!remoteObjects.isArray()) {
            throw new IllegalStateException("Remote object list is not an array");
        }
        String prefix = normalizePrefix(peer.pathPrefix());
        String remoteFilter = scope.remoteFilter();
        String localRoot = scope.localRoot();
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
            if (!remotePath.equals(remoteFilter) && !remotePath.startsWith(remoteFilter + ".")) {
                continue;
            }
            String suffix = remotePath.equals(remoteFilter)
                    ? ""
                    : remotePath.substring(remoteFilter.length());
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
        ensureAgentPath(localRoot, peer, null);
    }

    private void ensureSubtreeLocalRoot(String localRoot, FederationPeer peer) {
        ensureAgentPath(localRoot, peer, "Federated subtree from " + peer.baseUrl());
    }

    private void ensureAgentPath(String localRoot, FederationPeer peer, String defaultDescription) {
        if (objectManager.tree().findByPath(localRoot).isPresent()) {
            SystemObjectDescriptions.resolve(localRoot).ifPresent(entry ->
                    objectManager.updateInfo(localRoot, entry.displayName(), entry.description())
            );
            return;
        }
        int lastDot = localRoot.lastIndexOf('.');
        if (lastDot <= 0) {
            throw new IllegalStateException("Invalid local path: " + localRoot);
        }
        String parentPath = localRoot.substring(0, lastDot);
        if (objectManager.tree().findByPath(parentPath).isEmpty()) {
            if (FederationPaths.isCatalogMirrorPath(parentPath)) {
                ensureAgentPath(parentPath, peer, defaultDescription);
            } else {
                throw new IllegalStateException("Missing federation parent: " + parentPath);
            }
        }
        String name = localRoot.substring(lastDot + 1);
        SystemObjectDescriptions.Entry entry = SystemObjectDescriptions.resolve(localRoot)
                .orElse(new SystemObjectDescriptions.Entry(
                        peer.name(),
                        defaultDescription != null ? defaultDescription : "Federated catalog from " + peer.baseUrl()
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
