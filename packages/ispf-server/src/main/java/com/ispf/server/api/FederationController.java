package com.ispf.server.api;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ispf.server.federation.FederationAccessService;
import com.ispf.server.federation.FederationAuthMode;
import com.ispf.server.federation.FederationAuthStatus;
import com.ispf.server.federation.FederationBindService;
import com.ispf.server.federation.FederationCatalogService;
import com.ispf.server.federation.FederationConnectionMode;
import com.ispf.server.federation.FederationInboundRegistrationService;
import com.ispf.server.federation.FederationOutboundAgent;
import com.ispf.server.federation.FederationOutboundAgentService;
import com.ispf.server.federation.FederationPeer;
import com.ispf.server.federation.FederationPeerAuthService;
import com.ispf.server.federation.FederationPeerDraft;
import com.ispf.server.federation.FederationSecretsKeyService;
import com.ispf.server.federation.FederationService;
import com.ispf.server.federation.FederationTunnelHubService;
import com.ispf.server.federation.FederationTunnelSessionStore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/federation")
public class FederationController {

    private final FederationService federationService;
    private final FederationCatalogService federationCatalogService;
    private final FederationBindService federationBindService;
    private final FederationAccessService federationAccessService;
    private final FederationPeerAuthService authService;
    private final FederationInboundRegistrationService inboundRegistrationService;
    private final FederationOutboundAgentService outboundAgentService;
    private final FederationSecretsKeyService secretsKeyService;
    private final FederationTunnelHubService tunnelHubService;
    private final FederationTunnelSessionStore tunnelSessionStore;
    private final ObjectMapper objectMapper;

    public FederationController(
            FederationService federationService,
            FederationCatalogService federationCatalogService,
            FederationBindService federationBindService,
            FederationAccessService federationAccessService,
            FederationPeerAuthService authService,
            FederationInboundRegistrationService inboundRegistrationService,
            FederationOutboundAgentService outboundAgentService,
            FederationSecretsKeyService secretsKeyService,
            FederationTunnelHubService tunnelHubService,
            FederationTunnelSessionStore tunnelSessionStore,
            ObjectMapper objectMapper
    ) {
        this.federationService = federationService;
        this.federationCatalogService = federationCatalogService;
        this.federationBindService = federationBindService;
        this.federationAccessService = federationAccessService;
        this.authService = authService;
        this.inboundRegistrationService = inboundRegistrationService;
        this.outboundAgentService = outboundAgentService;
        this.secretsKeyService = secretsKeyService;
        this.tunnelHubService = tunnelHubService;
        this.tunnelSessionStore = tunnelSessionStore;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/peers")
    public List<FederationPeerDto> listPeers(Authentication authentication) {
        federationAccessService.requireAdmin(authentication);
        return federationService.listPeers().stream()
                .map(peer -> FederationPeerDto.from(peer, tunnelHubService.isConnected(peer.id())))
                .toList();
    }

    @PostMapping("/peers")
    public FederationPeerDto createPeer(
            Authentication authentication,
            @Valid @RequestBody FederationPeerRequest request
    ) {
        federationAccessService.requireAdmin(authentication);
        return FederationPeerDto.from(federationService.createPeer(request.toDraft(), request.toAuth()), false);
    }

    @PutMapping("/peers/{id}")
    public FederationPeerDto updatePeer(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody FederationPeerRequest request
    ) {
        federationAccessService.requireAdmin(authentication);
        return FederationPeerDto.from(
                federationService.updatePeer(id, request.toDraft(), request.toAuth()),
                tunnelHubService.isConnected(id)
        );
    }

    @DeleteMapping("/peers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePeer(Authentication authentication, @PathVariable UUID id) {
        federationAccessService.requireAdmin(authentication);
        federationService.deletePeer(id);
    }

    @GetMapping("/peers/{id}/auth-status")
    public FederationPeerAuthService.FederationPeerAuthStatus peerAuthStatus(
            Authentication authentication,
            @PathVariable UUID id
    ) {
        federationAccessService.requireAdmin(authentication);
        return authService.authStatus(id);
    }

    @PostMapping("/peers/{id}/refresh-token")
    public FederationPeerAuthService.FederationPeerAuthStatus refreshPeerToken(
            Authentication authentication,
            @PathVariable UUID id
    ) {
        federationAccessService.requireAdmin(authentication);
        return authService.refreshNow(id);
    }

    @PostMapping("/peers/{id}/sync-catalog")
    public SyncCatalogResponse syncCatalog(Authentication authentication, @PathVariable UUID id) {
        federationAccessService.requireAdmin(authentication);
        FederationCatalogService.SyncResult result = federationCatalogService.syncCatalog(id);
        return new SyncCatalogResponse(result.localRoot(), result.created(), result.updated(), result.remoteCount());
    }

    @GetMapping("/binds")
    public List<FederationBindService.FederationBindDto> listBinds(
            Authentication authentication,
            @RequestParam(defaultValue = "true") boolean excludeCatalogMirror
    ) {
        federationAccessService.requireAdmin(authentication);
        return federationBindService.list(excludeCatalogMirror);
    }

    @PostMapping("/binds")
    public FederationBindService.FederationBindDto createBind(
            Authentication authentication,
            @Valid @RequestBody CreateFederationBindRequest request
    ) {
        federationAccessService.requireAdmin(authentication);
        return federationBindService.bind(new FederationBindService.BindRequest(
                request.localPath(),
                request.parentPath(),
                request.name(),
                request.peerId(),
                request.remotePath(),
                request.displayName(),
                request.description()
        ));
    }

    @PatchMapping("/binds")
    public FederationBindService.FederationBindDto rebind(
            Authentication authentication,
            @Valid @RequestBody RebindFederationBindRequest request
    ) {
        federationAccessService.requireAdmin(authentication);
        return federationBindService.rebind(new FederationBindService.RebindRequest(
                request.localPath(),
                request.peerId(),
                request.remotePath()
        ));
    }

    @DeleteMapping("/binds")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unbind(Authentication authentication, @RequestParam @NotBlank String localPath) {
        federationAccessService.requireAdmin(authentication);
        federationBindService.unbind(localPath);
    }

    @PostMapping("/binds/probe")
    public FederationBindService.FederationBindProbeResult probeBind(
            Authentication authentication,
            @Valid @RequestBody ProbeFederationBindRequest request
    ) {
        federationAccessService.requireAdmin(authentication);
        return federationBindService.probe(request.peerId(), request.remotePath());
    }

    @GetMapping("/proxy/objects/by-path")
    public Object proxyObject(
            Authentication authentication,
            @RequestParam UUID peerId,
            @RequestParam String path
    ) {
        federationAccessService.assertProxyPathVisible(authentication, peerId, path);
        JsonNode json = federationService.proxyObjectByPath(peerId, path);
        return objectMapper.convertValue(json, Object.class);
    }

    @PatchMapping("/proxy/objects/by-path/variables/value")
    public Object proxyVariablePatch(
            Authentication authentication,
            @RequestParam UUID peerId,
            @RequestParam String path,
            @RequestParam String name,
            @RequestBody(required = false) Map<String, Object> body
    ) throws tools.jackson.core.JacksonException {
        federationAccessService.assertProxyPathVisible(authentication, peerId, path);
        JsonNode json = federationService.proxyVariablePatch(
                peerId,
                path,
                name,
                objectMapper.writeValueAsString(body != null ? body : Map.of())
        );
        return objectMapper.convertValue(json, Object.class);
    }

    @PostMapping("/proxy/objects/by-path/functions/invoke")
    public Object proxyFunctionInvoke(
            Authentication authentication,
            @RequestParam UUID peerId,
            @RequestParam String path,
            @RequestParam String name,
            @RequestBody(required = false) Map<String, Object> body
    ) throws tools.jackson.core.JacksonException {
        federationAccessService.assertProxyPathVisible(authentication, peerId, path);
        JsonNode json = federationService.proxyFunctionInvoke(
                peerId,
                path,
                name,
                objectMapper.writeValueAsString(body != null ? body : Map.of())
        );
        return objectMapper.convertValue(json, Object.class);
    }

    @PostMapping("/remote-token")
    public Map<String, Object> fetchRemoteToken(
            Authentication authentication,
            @Valid @RequestBody RemoteTokenRequest request
    ) {
        federationAccessService.requireAdmin(authentication);
        return federationService.fetchRemoteLoginToken(request.baseUrl(), request.username(), request.password());
    }

    @GetMapping("/inbound/registrations")
    public List<InboundRegistrationDto> listInboundRegistrations(Authentication authentication) {
        federationAccessService.requireAdmin(authentication);
        return inboundRegistrationService.list().stream().map(InboundRegistrationDto::from).toList();
    }

    @PostMapping("/inbound/registrations")
    public CreatedInboundRegistrationResponse createInboundRegistration(
            Authentication authentication,
            @Valid @RequestBody CreateInboundRegistrationRequest request
    ) {
        federationAccessService.requireAdmin(authentication);
        String createdBy = authentication != null ? authentication.getName() : "admin";
        var created = inboundRegistrationService.create(
                request.name(),
                request.pathPrefix(),
                request.ttlHours() != null ? request.ttlHours() : 24,
                createdBy
        );
        return new CreatedInboundRegistrationResponse(
                InboundRegistrationDto.from(created.registration()),
                created.registrationCode()
        );
    }

    @DeleteMapping("/inbound/registrations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteInboundRegistration(Authentication authentication, @PathVariable UUID id) {
        federationAccessService.requireAdmin(authentication);
        inboundRegistrationService.delete(id);
    }

    @GetMapping("/tunnels")
    public List<TunnelSessionDto> listTunnelSessions(Authentication authentication) {
        federationAccessService.requireAdmin(authentication);
        return tunnelSessionStore.listActive().stream().map(TunnelSessionDto::from).toList();
    }

    @GetMapping("/secrets-key/status")
    public SecretsKeyStatusDto secretsKeyStatus(Authentication authentication) {
        federationAccessService.requireAdmin(authentication);
        return SecretsKeyStatusDto.from(secretsKeyService);
    }

    @PostMapping("/secrets-key")
    public SecretsKeyStatusDto configureSecretsKey(
            Authentication authentication,
            @Valid @RequestBody ConfigureSecretsKeyRequest request
    ) {
        federationAccessService.requireAdmin(authentication);
        secretsKeyService.setUiKey(request.secretsKey());
        return SecretsKeyStatusDto.from(secretsKeyService);
    }

    @GetMapping("/outbound/agents")
    public List<OutboundAgentDto> listOutboundAgents(Authentication authentication) {
        federationAccessService.requireAdmin(authentication);
        return outboundAgentService.list().stream().map(OutboundAgentDto::from).toList();
    }

    @PostMapping("/outbound/agents")
    public OutboundAgentDto createOutboundAgent(
            Authentication authentication,
            @Valid @RequestBody CreateOutboundAgentRequest request
    ) {
        federationAccessService.requireAdmin(authentication);
        return OutboundAgentDto.from(outboundAgentService.create(
                request.name(),
                request.hubBaseUrl(),
                request.registrationCode(),
                request.pathPrefix()
        ));
    }

    @PutMapping("/outbound/agents/{id}")
    public OutboundAgentDto updateOutboundAgent(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOutboundAgentRequest request
    ) {
        federationAccessService.requireAdmin(authentication);
        return OutboundAgentDto.from(outboundAgentService.update(
                id,
                request.name(),
                request.hubBaseUrl(),
                request.registrationCode(),
                request.pathPrefix(),
                request.enabled() == null || request.enabled()
        ));
    }

    @DeleteMapping("/outbound/agents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOutboundAgent(Authentication authentication, @PathVariable UUID id) {
        federationAccessService.requireAdmin(authentication);
        outboundAgentService.delete(id);
    }

    @PostMapping("/outbound/agents/{id}/connect")
    public OutboundAgentDto connectOutboundAgent(Authentication authentication, @PathVariable UUID id) {
        federationAccessService.requireAdmin(authentication);
        return OutboundAgentDto.from(outboundAgentService.connectNow(id));
    }

    public record SyncCatalogResponse(
            String localRoot,
            int created,
            int updated,
            int remoteCount
    ) {
    }

    public record FederationPeerDto(
            UUID id,
            String name,
            String baseUrl,
            String pathPrefix,
            boolean enabled,
            String description,
            boolean hasAuthToken,
            FederationConnectionMode connectionMode,
            FederationAuthMode authMode,
            FederationAuthStatus authStatus,
            Instant tokenExpiresAt,
            boolean tunnelConnected
    ) {
        static FederationPeerDto from(FederationPeer peer, boolean tunnelConnected) {
            return new FederationPeerDto(
                    peer.id(),
                    peer.name(),
                    peer.baseUrl(),
                    peer.pathPrefix(),
                    peer.enabled(),
                    peer.description(),
                    peer.authToken() != null && !peer.authToken().isBlank(),
                    peer.connectionMode(),
                    peer.authMode(),
                    peer.authStatus(),
                    peer.tokenExpiresAt(),
                    tunnelConnected
            );
        }
    }

    public record FederationPeerRequest(
            @NotBlank String name,
            @NotBlank String baseUrl,
            String authToken,
            String pathPrefix,
            Boolean enabled,
            String description,
            FederationAuthMode authMode,
            String authUsername,
            String authPassword
    ) {
        FederationPeerDraft toDraft() {
            return new FederationPeerDraft(
                    name,
                    baseUrl,
                    authToken,
                    pathPrefix,
                    enabled == null || enabled,
                    description,
                    FederationConnectionMode.HTTP_PULL,
                    authMode != null ? authMode : FederationAuthMode.STATIC_TOKEN,
                    null,
                    authUsername,
                    authPassword,
                    null,
                    FederationAuthStatus.OK,
                    null,
                    null
            );
        }

        FederationPeerAuthService.FederationPeerRequestAuth toAuth() {
            return new FederationPeerAuthService.FederationPeerRequestAuth(authMode, authUsername, authPassword);
        }
    }

    public record RemoteTokenRequest(
            @NotBlank String baseUrl,
            @NotBlank String username,
            @NotBlank String password
    ) {
    }

    public record CreateInboundRegistrationRequest(
            @NotBlank String name,
            String pathPrefix,
            Integer ttlHours
    ) {
    }

    public record CreatedInboundRegistrationResponse(
            InboundRegistrationDto registration,
            String registrationCode
    ) {
    }

    public record InboundRegistrationDto(
            UUID id,
            String name,
            String pathPrefix,
            Instant expiresAt,
            Instant consumedAt
    ) {
        static InboundRegistrationDto from(
                com.ispf.server.federation.FederationInboundRegistrationStore.FederationInboundRegistration registration
        ) {
            return new InboundRegistrationDto(
                    registration.id(),
                    registration.name(),
                    registration.pathPrefix(),
                    registration.expiresAt(),
                    registration.consumedAt()
            );
        }
    }

    public record TunnelSessionDto(
            String sessionId,
            UUID peerId,
            UUID registrationId,
            Instant connectedAt,
            Instant lastPingAt
    ) {
        static TunnelSessionDto from(FederationTunnelSessionStore.FederationTunnelSession session) {
            return new TunnelSessionDto(
                    session.sessionId(),
                    session.peerId(),
                    session.registrationId(),
                    session.connectedAt(),
                    session.lastPingAt()
            );
        }
    }

    public record OutboundAgentDto(
            UUID id,
            String name,
            String hubBaseUrl,
            String pathPrefix,
            boolean enabled,
            String tunnelStatus,
            UUID linkedPeerId,
            String lastError,
            Instant lastConnectedAt
    ) {
        static OutboundAgentDto from(FederationOutboundAgent agent) {
            return new OutboundAgentDto(
                    agent.id(),
                    agent.name(),
                    agent.hubBaseUrl(),
                    agent.pathPrefix(),
                    agent.enabled(),
                    agent.tunnelStatus().name(),
                    agent.linkedPeerId(),
                    agent.lastError(),
                    agent.lastConnectedAt()
            );
        }
    }

    public record CreateOutboundAgentRequest(
            @NotBlank String name,
            @NotBlank String hubBaseUrl,
            @NotBlank String registrationCode,
            String pathPrefix
    ) {
    }

    public record UpdateOutboundAgentRequest(
            @NotBlank String name,
            @NotBlank String hubBaseUrl,
            String registrationCode,
            String pathPrefix,
            Boolean enabled
    ) {
    }

    public record SecretsKeyStatusDto(
            boolean configured,
            String source,
            boolean uiConfigurable
    ) {
        static SecretsKeyStatusDto from(FederationSecretsKeyService service) {
            return new SecretsKeyStatusDto(
                    service.isConfigured(),
                    service.source().name(),
                    service.isUiConfigurable()
            );
        }
    }

    public record ConfigureSecretsKeyRequest(
            @NotBlank String secretsKey
    ) {
    }

    public record CreateFederationBindRequest(
            String localPath,
            String parentPath,
            String name,
            UUID peerId,
            @NotBlank String remotePath,
            String displayName,
            String description
    ) {
    }

    public record RebindFederationBindRequest(
            @NotBlank String localPath,
            UUID peerId,
            @NotBlank String remotePath
    ) {
    }

    public record ProbeFederationBindRequest(
            UUID peerId,
            @NotBlank String remotePath
    ) {
    }
}
