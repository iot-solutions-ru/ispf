package com.ispf.server.federation;

import com.ispf.server.security.IspfSecretCipher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class FederationOutboundAgentService {

    private final FederationOutboundAgentStore agentStore;
    private final IspfSecretCipher secretCipher;
    private final FederationTunnelAgentService tunnelAgentService;

    public FederationOutboundAgentService(
            FederationOutboundAgentStore agentStore,
            IspfSecretCipher secretCipher,
            @Lazy FederationTunnelAgentService tunnelAgentService
    ) {
        this.agentStore = agentStore;
        this.secretCipher = secretCipher;
        this.tunnelAgentService = tunnelAgentService;
    }

    public List<FederationOutboundAgent> list() {
        return agentStore.listAll();
    }

    @Transactional
    public FederationOutboundAgent create(String name, String hubBaseUrl, String registrationCode, String pathPrefix) {
        if (!secretCipher.isEnabled()) {
            throw new IllegalStateException("ispf.security.secrets-key is required to store outbound agent credentials");
        }
        FederationOutboundAgent agent = agentStore.insert(new FederationOutboundAgentDraft(
                name,
                hubBaseUrl,
                secretCipher.encrypt(registrationCode),
                null,
                pathPrefix,
                true,
                null
        ));
        tunnelAgentService.scheduleConnect(agent.id());
        return agentStore.findById(agent.id()).orElseThrow();
    }

    @Transactional
    public FederationOutboundAgent update(
            UUID id,
            String name,
            String hubBaseUrl,
            String registrationCode,
            String pathPrefix,
            boolean enabled
    ) {
        FederationOutboundAgent current = agentStore.findById(id).orElseThrow();
        String codeEnc = current.registrationCodeEnc();
        if (registrationCode != null && !registrationCode.isBlank()) {
            codeEnc = secretCipher.encrypt(registrationCode);
        }
        FederationOutboundAgent updated = agentStore.update(id, new FederationOutboundAgentDraft(
                name,
                hubBaseUrl,
                codeEnc,
                current.sessionTokenEnc(),
                pathPrefix,
                enabled,
                current.linkedPeerId()
        ));
        if (enabled) {
            tunnelAgentService.scheduleConnect(id);
        } else {
            tunnelAgentService.disconnect(id);
        }
        return updated;
    }

    @Transactional
    public void delete(UUID id) {
        tunnelAgentService.disconnect(id);
        agentStore.delete(id);
    }

    public FederationOutboundAgent connectNow(UUID id) {
        tunnelAgentService.connectNow(id);
        return agentStore.findById(id).orElseThrow();
    }
}
