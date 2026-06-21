package com.ispf.server.federation;

import com.ispf.server.security.PlatformUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FederationInboundRegistrationService {

    private final FederationInboundRegistrationStore registrationStore;
    private final FederationPeerStore peerStore;
    private final PlatformUserService platformUserService;

    public FederationInboundRegistrationService(
            FederationInboundRegistrationStore registrationStore,
            FederationPeerStore peerStore,
            PlatformUserService platformUserService
    ) {
        this.registrationStore = registrationStore;
        this.peerStore = peerStore;
        this.platformUserService = platformUserService;
    }

    public record CreatedRegistration(
            FederationInboundRegistrationStore.FederationInboundRegistration registration,
            String registrationCode
    ) {
    }

    @Transactional
    public CreatedRegistration create(String name, String pathPrefix, int ttlHours, String createdBy) {
        String code = FederationRegistrationCodes.generate();
        Instant expiresAt = Instant.now().plus(Math.max(1, ttlHours), ChronoUnit.HOURS);
        var registration = registrationStore.insert(
                name,
                FederationRegistrationCodes.hash(code),
                pathPrefix,
                expiresAt,
                createdBy
        );
        return new CreatedRegistration(registration, code);
    }

    public List<FederationInboundRegistrationStore.FederationInboundRegistration> list() {
        return registrationStore.listAll();
    }

    @Transactional
    public void delete(UUID id) {
        registrationStore.delete(id);
    }

    @Transactional
    public FederationTunnelRegistrationResult consumeRegistration(
            String registrationCode,
            String siteName,
            String pathPrefixOverride
    ) {
        var registration = registrationStore.findValidByCodeHash(
                FederationRegistrationCodes.hash(registrationCode),
                Instant.now()
        ).orElseThrow(() -> new IllegalArgumentException("Invalid or expired registration code"));

        String peerName = siteName == null || siteName.isBlank() ? registration.name() : siteName.trim();
        String pathPrefix = pathPrefixOverride == null || pathPrefixOverride.isBlank()
                ? registration.pathPrefix()
                : pathPrefixOverride.trim();

        FederationPeer peer = peerStore.findByName(peerName).orElseGet(() -> {
            FederationPeerDraft draft = FederationPeerDraft.tunnelPeer(
                    peerName,
                    pathPrefix,
                    "Tunnel inbound from " + peerName
            );
            return peerStore.insert(draft);
        });

        if (peer.connectionMode() != FederationConnectionMode.TUNNEL_INBOUND) {
            peer = peerStore.update(peer.id(), FederationPeerDraft.tunnelPeer(
                    peer.name(),
                    pathPrefix,
                    peer.description()
            ));
        }

        registrationStore.markConsumed(registration.id(), Instant.now());

        Map<String, Object> tokenResponse = platformUserService.issueFederationToken("admin", 168);
        return new FederationTunnelRegistrationResult(
                peer,
                (String) tokenResponse.get("token"),
                tokenResponse.get("expiresAt") != null ? tokenResponse.get("expiresAt").toString() : null,
                registration.id()
        );
    }

    public FederationPeer resolveReconnectPeer(String sessionToken, UUID peerId) {
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new IllegalArgumentException("sessionToken is required for reconnect");
        }
        platformUserService.authenticateToken(sessionToken.trim())
                .orElseThrow(() -> new IllegalArgumentException("Invalid session token"));
        FederationPeer peer = peerStore.findById(peerId)
                .orElseThrow(() -> new IllegalArgumentException("Peer not found: " + peerId));
        if (peer.connectionMode() != FederationConnectionMode.TUNNEL_INBOUND) {
            throw new IllegalArgumentException("Peer is not a tunnel inbound peer");
        }
        return peer;
    }

    public record FederationTunnelRegistrationResult(
            FederationPeer peer,
            String sessionToken,
            String tokenExpiresAt,
            UUID registrationId
    ) {
    }
}
