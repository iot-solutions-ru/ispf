package com.ispf.server.federation;

import com.ispf.server.security.IspfSecretCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@Service
public class FederationPeerAuthService {

    private static final Logger log = LoggerFactory.getLogger(FederationPeerAuthService.class);

    private final FederationPeerStore peerStore;
    private final FederationService federationService;
    private final IspfSecretCipher secretCipher;

    public FederationPeerAuthService(
            FederationPeerStore peerStore,
            @Lazy FederationService federationService,
            IspfSecretCipher secretCipher
    ) {
        this.peerStore = peerStore;
        this.federationService = federationService;
        this.secretCipher = secretCipher;
    }

    public FederationPeerDraft enrichDraft(FederationPeerDraft base, FederationPeerRequestAuth auth) {
        FederationAuthMode authMode = auth != null && auth.authMode() != null
                ? auth.authMode()
                : (base.authMode() != null ? base.authMode() : FederationAuthMode.STATIC_TOKEN);
        String username = auth != null && auth.authUsername() != null ? auth.authUsername() : base.authUsername();
        String passwordPlain = auth != null ? auth.authPassword() : null;
        String secretEnc = base.authSecretEnc();
        Instant tokenExpiresAt = base.tokenExpiresAt();
        String authToken = base.authToken();
        FederationAuthStatus status = FederationAuthStatus.OK;
        Instant lastAuthAt = base.lastAuthAt();

        if (authMode == FederationAuthMode.SERVICE_ACCOUNT) {
            if (passwordPlain != null && !passwordPlain.isBlank()) {
                secretEnc = secretCipher.encrypt(passwordPlain);
            }
            if (username == null || username.isBlank() || secretEnc == null || secretEnc.isBlank()) {
                throw new IllegalArgumentException(
                        "SERVICE_ACCOUNT requires authUsername and authPassword"
                );
            }
            if (passwordPlain != null && !passwordPlain.isBlank()) {
                var login = federationService.fetchRemoteLoginToken(base.baseUrl(), username, passwordPlain);
                authToken = (String) login.get("token");
                tokenExpiresAt = parseExpiresAt(login.get("expiresAt"));
                lastAuthAt = Instant.now();
            }
        }

        return new FederationPeerDraft(
                base.name(),
                base.baseUrl(),
                authToken,
                base.pathPrefix(),
                base.enabled(),
                base.description(),
                base.connectionMode(),
                authMode,
                tokenExpiresAt,
                username,
                null,
                secretEnc,
                status,
                lastAuthAt,
                null
        );
    }

    public FederationPeerDraft mergeUpdate(UUID id, FederationPeerDraft draft, FederationPeerRequestAuth auth) {
        FederationPeer current = peerStore.findById(id).orElseThrow();
        FederationAuthMode authMode = auth != null && auth.authMode() != null
                ? auth.authMode()
                : current.authMode();
        String username = auth != null && auth.authUsername() != null
                ? auth.authUsername()
                : current.authUsername();
        String secretEnc = current.authSecretEnc();
        if (auth != null && auth.authPassword() != null && !auth.authPassword().isBlank()) {
            secretEnc = secretCipher.encrypt(auth.authPassword());
        }
        String authToken = draft.authToken() != null ? draft.authToken() : current.authToken();
        Instant tokenExpiresAt = draft.tokenExpiresAt() != null ? draft.tokenExpiresAt() : current.tokenExpiresAt();

        if (authMode == FederationAuthMode.SERVICE_ACCOUNT
                && auth != null
                && auth.authPassword() != null
                && !auth.authPassword().isBlank()) {
            var login = federationService.fetchRemoteLoginToken(draft.baseUrl(), username, auth.authPassword());
            authToken = (String) login.get("token");
            tokenExpiresAt = parseExpiresAt(login.get("expiresAt"));
        }

        return new FederationPeerDraft(
                draft.name(),
                draft.baseUrl(),
                authToken,
                draft.pathPrefix(),
                draft.enabled(),
                draft.description(),
                draft.connectionMode() != null ? draft.connectionMode() : current.connectionMode(),
                authMode,
                tokenExpiresAt,
                username,
                null,
                secretEnc,
                FederationAuthStatus.OK,
                authToken != null ? Instant.now() : current.lastAuthAt(),
                null
        );
    }

    public FederationPeerAuthStatus authStatus(UUID peerId) {
        FederationPeer peer = peerStore.findById(peerId).orElseThrow();
        return new FederationPeerAuthStatus(
                peer.id(),
                peer.authMode(),
                peer.authStatus(),
                peer.tokenExpiresAt(),
                peer.lastAuthAt(),
                peer.lastAuthError(),
                peer.hasServiceAccount()
        );
    }

    public FederationPeerAuthStatus refreshNow(UUID peerId) {
        refreshPeer(peerStore.findById(peerId).orElseThrow());
        return authStatus(peerId);
    }

    @Scheduled(fixedDelayString = "${ispf.federation.auth-refresh-ms:900000}")
    public void refreshDueTokens() {
        Instant now = Instant.now();
        for (FederationPeer peer : peerStore.listServiceAccountPeers()) {
            if (shouldRefresh(peer, now)) {
                try {
                    refreshPeer(peer);
                } catch (Exception e) {
                    log.warn("Federation auth refresh failed for {}: {}", peer.name(), e.getMessage());
                }
            }
        }
    }

    public boolean refreshPeerIfUnauthorized(UUID peerId) {
        FederationPeer peer = peerStore.findById(peerId).orElseThrow();
        if (peer.authMode() != FederationAuthMode.SERVICE_ACCOUNT || !peer.hasServiceAccount()) {
            return false;
        }
        refreshPeer(peer);
        return true;
    }

    private void refreshPeer(FederationPeer peer) {
        if (peer.authMode() != FederationAuthMode.SERVICE_ACCOUNT || !peer.hasServiceAccount()) {
            throw new IllegalStateException("Peer is not configured for service-account refresh: " + peer.name());
        }
        try {
            String password = secretCipher.decrypt(peer.authSecretEnc());
            var login = federationService.fetchRemoteLoginToken(peer.baseUrl(), peer.authUsername(), password);
            String token = (String) login.get("token");
            Instant expiresAt = parseExpiresAt(login.get("expiresAt"));
            peerStore.updateAuthState(
                    peer.id(),
                    token,
                    expiresAt,
                    FederationAuthStatus.OK,
                    Instant.now(),
                    null
            );
        } catch (RuntimeException e) {
            peerStore.updateAuthState(
                    peer.id(),
                    peer.authToken(),
                    peer.tokenExpiresAt(),
                    FederationAuthStatus.FAILED,
                    peer.lastAuthAt(),
                    e.getMessage()
            );
            throw e;
        }
    }

    static boolean shouldRefresh(FederationPeer peer, Instant now) {
        if (peer.tokenExpiresAt() == null) {
            return true;
        }
        long ttlSeconds = ChronoUnit.SECONDS.between(now, peer.tokenExpiresAt());
        if (ttlSeconds <= 0) {
            return true;
        }
        long refreshSkew = Math.max(3600L, ttlSeconds / 5);
        return ttlSeconds <= refreshSkew;
    }

    static Instant parseExpiresAt(Object raw) {
        if (raw == null) {
            return Instant.now().plus(12, ChronoUnit.HOURS);
        }
        if (raw instanceof Instant instant) {
            return instant;
        }
        return Instant.parse(raw.toString());
    }

    public record FederationPeerAuthStatus(
            UUID peerId,
            FederationAuthMode authMode,
            FederationAuthStatus authStatus,
            Instant tokenExpiresAt,
            Instant lastAuthAt,
            String lastAuthError,
            boolean serviceAccountConfigured
    ) {
    }

    public record FederationPeerRequestAuth(
            FederationAuthMode authMode,
            String authUsername,
            String authPassword
    ) {
    }
}
