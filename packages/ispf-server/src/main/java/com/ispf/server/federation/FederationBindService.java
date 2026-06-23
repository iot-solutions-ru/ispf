package com.ispf.server.federation;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.config.EmbeddedServerPort;
import com.ispf.server.driver.DriverRuntimeService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.ObjectTreePolicy;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class FederationBindService {

    private static final Logger log = LoggerFactory.getLogger(FederationBindService.class);

    private final FederationPeerStore peerStore;
    private final FederationService federationService;
    private final FederationProxyService federationProxyService;
    private final ObjectManager objectManager;
    private final DriverRuntimeService driverRuntimeService;
    private final EmbeddedServerPort embeddedServerPort;
    private final Environment environment;
    private final ObjectMapper objectMapper;

    public FederationBindService(
            FederationPeerStore peerStore,
            FederationService federationService,
            FederationProxyService federationProxyService,
            ObjectManager objectManager,
            DriverRuntimeService driverRuntimeService,
            EmbeddedServerPort embeddedServerPort,
            Environment environment,
            ObjectMapper objectMapper
    ) {
        this.peerStore = peerStore;
        this.federationService = federationService;
        this.federationProxyService = federationProxyService;
        this.objectManager = objectManager;
        this.driverRuntimeService = driverRuntimeService;
        this.embeddedServerPort = embeddedServerPort;
        this.environment = environment;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FederationBindDto bind(BindRequest request) {
        FederationPeer peer = requireEnabledPeer(request.peerId());
        JsonNode remote = probeRemote(peer.id(), request.remotePath());
        String localPath = resolveLocalPath(request);
        assertBindTargetAllowed(localPath, request.remotePath(), peer);
        if (FederationProxyMetadata.isProxy(objectManager.require(localPath))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Object is already federation-bound: " + localPath
            );
        }
        pauseLocalDriverIfNeeded(localPath);
        FederationBindSnapshot.captureIfAbsent(
                objectManager,
                driverRuntimeService,
                objectMapper,
                localPath
        );
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
        assertBindTargetAllowed(request.localPath(), request.remotePath(), peer);
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
        Optional<FederationBindSnapshot.LocalState> restored = FederationBindSnapshot.restoreAndClear(
                objectManager,
                objectMapper,
                localPath
        );
        restored.ifPresent(state -> resumeLocalDriverIfNeeded(localPath, state.driverWasRunning()));
        objectManager.persistNodeTree(localPath);
        PlatformObject restoredNode = objectManager.require(localPath);
        return new FederationBindDto(
                localPath,
                null,
                null,
                null,
                restoredNode.type(),
                restoredNode.displayName(),
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
        ObjectTreePolicy.assertParentAllowsStructuralChildren(parentPath, objectManager);
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

    private void assertBindTargetAllowed(String localPath, String remotePath, FederationPeer peer) {
        FederationBindRules.validate(
                localPath,
                remotePath,
                peer,
                effectiveLocalServerPort(),
                federationProxyService.resolve(remotePath.trim())
        );
    }

    private int effectiveLocalServerPort() {
        return embeddedServerPort.get(environment);
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

    private void resumeLocalDriverIfNeeded(String localPath, boolean driverWasRunning) {
        if (!driverWasRunning) {
            return;
        }
        PlatformObject node = objectManager.require(localPath);
        if (node.type() != ObjectType.DEVICE) {
            return;
        }
        try {
            driverRuntimeService.start(localPath);
        } catch (RuntimeException ex) {
            log.warn("Failed to restart driver after federation unbind for {}: {}", localPath, ex.getMessage());
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
