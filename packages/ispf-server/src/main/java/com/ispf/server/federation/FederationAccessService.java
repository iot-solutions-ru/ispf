package com.ispf.server.federation;

import com.ispf.server.config.IspfRoles;
import com.ispf.server.tenant.TenantScopeService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FederationAccessService {

    private final FederationPeerStore peerStore;
    private final TenantScopeService tenantScopeService;

    public FederationAccessService(FederationPeerStore peerStore, TenantScopeService tenantScopeService) {
        this.peerStore = peerStore;
        this.tenantScopeService = tenantScopeService;
    }

    public void requireAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || !isAdmin(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Federation peer management requires admin role");
        }
    }

    public void assertProxyPathVisible(Authentication authentication, java.util.UUID peerId, String objectPath) {
        FederationPeer peer = peerStore.findById(peerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Peer not found: " + peerId));
        String localPath = FederationService.localMirrorPath(peer, objectPath);
        if (!tenantScopeService.isPathVisible(localPath, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Path not visible in tenant scope: " + localPath);
        }
    }

    private static boolean isAdmin(Authentication authentication) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (("ROLE_" + IspfRoles.ADMIN).equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
