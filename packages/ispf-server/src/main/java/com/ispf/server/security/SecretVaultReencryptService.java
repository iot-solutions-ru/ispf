package com.ispf.server.security;

import com.ispf.server.federation.FederationOutboundAgent;
import com.ispf.server.federation.FederationOutboundAgentStore;
import com.ispf.server.federation.FederationPeer;
import com.ispf.server.federation.FederationPeerStore;
import com.ispf.server.persistence.PlatformCredentialRepository;
import com.ispf.server.persistence.entity.PlatformCredentialEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Re-encrypts vault and federation ciphertext under the active secrets-key (ADR-0065).
 */
@Service
public class SecretVaultReencryptService {

    private final IspfSecretCipher cipher;
    private final PlatformCredentialRepository credentialRepository;
    private final FederationOutboundAgentStore outboundAgentStore;
    private final FederationPeerStore peerStore;

    public SecretVaultReencryptService(
            IspfSecretCipher cipher,
            PlatformCredentialRepository credentialRepository,
            FederationOutboundAgentStore outboundAgentStore,
            FederationPeerStore peerStore
    ) {
        this.cipher = cipher;
        this.credentialRepository = credentialRepository;
        this.outboundAgentStore = outboundAgentStore;
        this.peerStore = peerStore;
    }

    @Transactional
    public Map<String, Object> reencryptAll() {
        if (!cipher.isEnabled()) {
            throw new IllegalStateException("ispf.security.secrets-key is required to re-encrypt secrets");
        }
        int credentials = reencryptCredentials();
        int outbound = reencryptOutboundAgents();
        int peers = reencryptPeers();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "OK");
        result.put("activeKeyId", cipher.activeKeyId());
        result.put("credentialsUpdated", credentials);
        result.put("outboundAgentsUpdated", outbound);
        result.put("peersUpdated", peers);
        return result;
    }

    private int reencryptCredentials() {
        int updated = 0;
        for (PlatformCredentialEntity entity : credentialRepository.findAll()) {
            String ciphertext = entity.getCipherText();
            if (!cipher.needsReencrypt(ciphertext)) {
                continue;
            }
            String plain = cipher.decrypt(ciphertext);
            entity.setCipherText(cipher.encrypt(plain));
            entity.setUpdatedAt(Instant.now());
            credentialRepository.save(entity);
            updated++;
        }
        return updated;
    }

    private int reencryptOutboundAgents() {
        int updated = 0;
        for (FederationOutboundAgent agent : outboundAgentStore.listAll()) {
            boolean touch = cipher.needsReencrypt(agent.registrationCodeEnc())
                    || cipher.needsReencrypt(agent.sessionTokenEnc());
            if (!touch) {
                continue;
            }
            outboundAgentStore.updateEncryptedSecrets(
                    agent.id(),
                    rewrite(agent.registrationCodeEnc()),
                    rewrite(agent.sessionTokenEnc())
            );
            updated++;
        }
        return updated;
    }

    private int reencryptPeers() {
        int updated = 0;
        for (FederationPeer peer : peerStore.listAll()) {
            if (!cipher.needsReencrypt(peer.authSecretEnc())) {
                continue;
            }
            peerStore.updateAuthSecretEnc(peer.id(), rewrite(peer.authSecretEnc()));
            updated++;
        }
        return updated;
    }

    private String rewrite(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return ciphertext;
        }
        if (!cipher.needsReencrypt(ciphertext)) {
            return ciphertext;
        }
        return cipher.encrypt(cipher.decrypt(ciphertext));
    }
}
