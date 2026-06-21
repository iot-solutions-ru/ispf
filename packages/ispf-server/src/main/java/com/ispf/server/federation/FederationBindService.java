package com.ispf.server.federation;

import tools.jackson.databind.JsonNode;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.driver.DriverRuntimeService;
import com.ispf.server.object.ObjectManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class FederationBindService {

    private final FederationPeerStore peerStore;
    private final FederationService federationService;
    private final FederationProxyService federationProxyService;
    private final ObjectManager objectManager;
    private final DriverRuntimeService driverRuntimeService;

    public FederationBindService(
            FederationPeerStore peerStore,
            FederationService federationService,
            FederationProxyService federationProxyService,
            ObjectManager objectManager,
            DriverRuntimeService driverRuntimeService
    ) {
        this.peerStore = peerStore;
        this.federationService = federationService;
        this.federationProxyService = federationProxyService;
        this.objectManager = objectManager;
        this.driverRuntimeService = driverRuntimeService;
    }

    @Transactional
    public FederationBindDto bind(BindRequest request) {
        FederationPeer peer = requireEnabledPeer(request.peerId());
        JsonNode remote = probeRemote(peer.id(), request.remotePath());
        String localPath = resolveLocalPath(request);
        assertBindTargetAllowed(localPath, request.remotePath());
        if (FederationProxyMetadata.isProxy(objectManager.require(localPath))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Object is already federation-bound: " + localPath
            );
        }
        pauseLocalDriverIfNeeded(localPath);
        FederationProxyNodeHelper.syncFromRemoteProbe(objectManager, localPath, remote);
        FederationProxyNodeHelper.markProxy(objectManager, localPath, peer.id(), request.remotePath().trim());
        return toDto(objectManager.require(localPath), peer);
    }

    @Transactional
    public FederationBindDto rebind(RebindRequest request) {
        FederationPeer peer = requireEnabledPeer(request.peerId());
        PlatformObject node = objectManager.require(request.localPath());
        if (!FederationProxyMetadata.isProxy(node)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Object is not federation-bound: " + request.localPath()
            );
        }
        assertBindTargetAllowed(request.localPath(), request.remotePath());
        JsonNode remote = probeRemote(peer.id(), request.remotePath());
        FederationProxyNodeHelper.syncFromRemoteProbe(objectManager, request.localPath(), remote);
        FederationProxyNodeHelper.markProxy(
                objectManager,
                request.localPath(),
                peer.id(),
                request.remotePath().trim()
        );
        return toDto(objectManager.require(request.localPath()), peer);
    }

    @Transactional
    public FederationBindDto unbind(String localPath) {
        PlatformObject node = objectManager.require(localPath);
        if (!FederationProxyMetadata.isProxy(node)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Object is not federation-bound: " + localPath
            );
        }
        FederationProxyMetadata.clearFrom(node);
        objectManager.deleteVariable(localPath, FederationProxyMetadata.VAR_PROXY);
        objectManager.deleteVariable(localPath, FederationProxyMetadata.VAR_PEER_ID);
        objectManager.deleteVariable(localPath, FederationProxyMetadata.VAR_REMOTE_PATH);
        objectManager.persistNodeTree(localPath);
        return new FederationBindDto(
                localPath,
                null,
                null,
                null,
                node.type(),
                node.displayName(),
                false
        );
    }

    public List<FederationBindDto> list(boolean excludeCatalogMirror) {
        List<FederationBindDto> binds = new ArrayList<>();
        for (PlatformObject node : objectManager.tree().all()) {
            if (!FederationProxyMetadata.isProxy(node)) {
                continue;
            }
            if (excludeCatalogMirror && FederationPaths.isCatalogMirrorPath(node.path())) {
                continue;
            }
            Optional<UUID> peerId = FederationProxyMetadata.peerId(node);
            Optional<String> remotePath = FederationProxyMetadata.remotePath(node);
            if (peerId.isEmpty() || remotePath.isEmpty()) {
                continue;
            }
            FederationPeer peer = peerStore.findById(peerId.get()).orElse(null);
            binds.add(toDto(node, peer));
        }
        return binds;
    }

    public FederationBindProbeResult probe(UUID peerId, String remotePath) {
        requireEnabledPeer(peerId);
        JsonNode remote = probeRemote(peerId, remotePath);
        return new FederationBindProbeResult(
                remotePath.trim(),
                FederationProxyNodeHelper.parseType(
                        FederationProxyNodeHelper.textOrDefault(remote, "type", "AGENT")
                ).name(),
                FederationProxyNodeHelper.textOrDefault(remote, "displayName", ""),
                FederationProxyNodeHelper.textOrDefault(remote, "description", "")
        );
    }

    public void assertParentAllowsChildren(String parentPath) {
        objectManager.tree().findByPath(parentPath).ifPresent(parent -> {
            if (FederationProxyMetadata.isProxy(parent)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Cannot add children under federation-bound object: " + parentPath
                );
            }
        });
    }

    private String resolveLocalPath(BindRequest request) {
        if (request.localPath() != null && !request.localPath().isBlank()) {
            objectManager.require(request.localPath().trim());
            return request.localPath().trim();
        }
        if (request.parentPath() == null || request.parentPath().isBlank()
                || request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Either localPath or parentPath+name is required"
            );
        }
        String parentPath = request.parentPath().trim();
        String name = request.name().trim();
        assertParentAllowsChildren(parentPath);
        String fullPath = objectManager.tree().resolveChildPath(parentPath, name);
        if (objectManager.tree().findByPath(fullPath).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Object exists: " + fullPath);
        }
        ObjectType type = ObjectType.CUSTOM;
        if (request.remotePath() != null && !request.remotePath().isBlank()) {
            try {
                FederationPeer peer = requireEnabledPeer(request.peerId());
                JsonNode remote = probeRemote(peer.id(), request.remotePath());
                type = FederationProxyNodeHelper.parseType(
                        FederationProxyNodeHelper.textOrDefault(remote, "type", "CUSTOM")
                );
            } catch (ResponseStatusException ex) {
                if (ex.getStatusCode() != HttpStatus.BAD_GATEWAY) {
                    throw ex;
                }
            }
        }
        String displayName = request.displayName() != null && !request.displayName().isBlank()
                ? request.displayName().trim()
                : name;
        String description = request.description() != null ? request.description() : "";
        objectManager.create(parentPath, name, type, displayName, description, null);
        return fullPath;
    }

    private void assertBindTargetAllowed(String localPath, String remotePath) {
        if (remotePath == null || remotePath.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "remotePath is required");
        }
        String trimmedRemote = remotePath.trim();
        federationProxyService.resolve(trimmedRemote).ifPresent(target -> {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "remotePath must not reference a local federation-bound path: " + trimmedRemote
            );
        });
        if (trimmedRemote.equals(localPath)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "remotePath must differ from localPath"
            );
        }
    }

    private JsonNode probeRemote(UUID peerId, String remotePath) {
        try {
            return federationService.proxyObjectByPath(peerId, remotePath.trim());
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Remote probe failed: " + ex.getMessage(),
                    ex
            );
        }
    }

    private FederationPeer requireEnabledPeer(UUID peerId) {
        FederationPeer peer = peerStore.findById(peerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Peer not found: " + peerId));
        if (!peer.enabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Peer is disabled: " + peer.name());
        }
        return peer;
    }

    private void pauseLocalDriverIfNeeded(String localPath) {
        PlatformObject node = objectManager.require(localPath);
        if (node.type() == ObjectType.DEVICE) {
            driverRuntimeService.stopIfRunning(localPath);
        }
    }

    private FederationBindDto toDto(PlatformObject node, FederationPeer peer) {
        UUID peerId = FederationProxyMetadata.peerId(node).orElse(null);
        String remotePath = FederationProxyMetadata.remotePath(node).orElse(null);
        return new FederationBindDto(
                node.path(),
                peerId,
                peer != null ? peer.name() : null,
                remotePath,
                node.type(),
                node.displayName(),
                FederationProxyMetadata.isProxy(node)
        );
    }

    public record BindRequest(
            String localPath,
            String parentPath,
            String name,
            UUID peerId,
            String remotePath,
            String displayName,
            String description
    ) {
    }

    public record RebindRequest(
            String localPath,
            UUID peerId,
            String remotePath
    ) {
    }

    public record FederationBindDto(
            String localPath,
            UUID peerId,
            String peerName,
            String remotePath,
            ObjectType type,
            String displayName,
            boolean bound
    ) {
    }

    public record FederationBindProbeResult(
            String remotePath,
            String type,
            String displayName,
            String description
    ) {
    }
}
