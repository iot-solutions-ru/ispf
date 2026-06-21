package com.ispf.server.federation;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Optional;

/**
 * Validation rules for federation bind targets (no cycles / self-proxy on same instance).
 */
public final class FederationBindRules {

    private FederationBindRules() {
    }

    public static void validate(
            String localPath,
            String remotePath,
            FederationPeer peer,
            int localServerPort,
            Optional<FederationProxyService.FederationProxyTarget> localProxyAtRemotePath
    ) {
        if (remotePath == null || remotePath.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "remotePath is required");
        }
        String trimmedRemote = remotePath.trim();
        String trimmedLocal = localPath.trim();

        localProxyAtRemotePath.ifPresent(target -> {
            if (target.localPath().equals(trimmedLocal)) {
                if (peerTargetsLocalInstance(peer, localServerPort)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Cannot bind a path to itself on the same ISPF instance (infinite proxy loop). "
                                    + "Use a remote peer, or choose a different remotePath."
                    );
                }
                return;
            }
            if (target.remotePath().equals(trimmedLocal)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "remotePath references a federated mirror that already targets this local path: "
                                + trimmedRemote
                );
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "remotePath must not reference a local federation-bound path: " + trimmedRemote
            );
        });

        if (trimmedRemote.equals(trimmedLocal) && peerTargetsLocalInstance(peer, localServerPort)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot bind a path to itself on the same ISPF instance (infinite proxy loop). "
                            + "Use a remote peer, or choose a different remotePath."
            );
        }
    }

    static boolean peerTargetsLocalInstance(FederationPeer peer, int localServerPort) {
        if (peer == null || peer.baseUrl() == null || peer.baseUrl().isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(peer.baseUrl().trim());
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            boolean localHost = "127.0.0.1".equals(host)
                    || "localhost".equalsIgnoreCase(host)
                    || "::1".equals(host);
            if (!localHost) {
                return false;
            }
            int peerPort = uri.getPort();
            if (peerPort <= 0) {
                peerPort = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
            }
            return peerPort == localServerPort;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
