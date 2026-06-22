package com.ispf.server.ai.api;

import com.ispf.server.ai.agent.AgentSession;
import com.ispf.server.ai.agent.AgentSessionStore;
import com.ispf.server.ai.agent.PlatformAgentToolRegistry;
import com.ispf.server.ai.agent.TreeFirstAgentService;
import com.ispf.server.ai.generation.AiBundleGenerationService;
import com.ispf.server.ai.llm.LlmProviderRegistry;
import com.ispf.server.ai.tool.AiToolRegistry;
import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import com.ispf.server.application.bundle.BundleManifestJsonSupport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiToolRegistry toolRegistry;
    private final LlmProviderRegistry llmProviderRegistry;
    private final AiBundleGenerationService generationService;
    private final TreeFirstAgentService agentService;
    private final AgentSessionStore agentSessionStore;
    private final PlatformAgentToolRegistry agentToolRegistry;
    private final ObjectMapper objectMapper;

    public AiController(
            AiToolRegistry toolRegistry,
            LlmProviderRegistry llmProviderRegistry,
            AiBundleGenerationService generationService,
            TreeFirstAgentService agentService,
            AgentSessionStore agentSessionStore,
            PlatformAgentToolRegistry agentToolRegistry,
            ObjectMapper objectMapper
    ) {
        this.toolRegistry = toolRegistry;
        this.llmProviderRegistry = llmProviderRegistry;
        this.generationService = generationService;
        this.agentService = agentService;
        this.agentSessionStore = agentSessionStore;
        this.agentToolRegistry = agentToolRegistry;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/models")
    public Map<String, Object> listModels(Authentication authentication) {
        return toolRegistry.listModels(actor(authentication));
    }

    @GetMapping("/provider")
    public Map<String, Object> providerStatus() {
        return llmProviderRegistry.status();
    }

    @GetMapping("/tools/context-pack")
    public Map<String, Object> contextPackInfo() {
        return toolRegistry.contextPackInfo();
    }

    @PostMapping("/tools/validate-bundle")
    public Map<String, Object> validateBundle(
            Authentication authentication,
            @Valid @RequestBody BundleToolRequest request
    ) {
        return toolRegistry.validateBundle(
                request.appId(),
                parseManifest(request.manifest()),
                actor(authentication)
        );
    }

    @PostMapping("/tools/dry-run-deploy")
    public Map<String, Object> dryRunDeploy(
            Authentication authentication,
            @Valid @RequestBody BundleToolRequest request
    ) {
        return toolRegistry.dryRunDeploy(
                request.appId(),
                parseManifest(request.manifest()),
                actor(authentication)
        );
    }

    @GetMapping("/agent/tools")
    public Map<String, Object> agentTools() {
        return Map.of("tools", agentToolRegistry.toolCatalog());
    }

    @PostMapping("/agent/sessions")
    public Map<String, Object> createAgentSession(
            Authentication authentication,
            @Valid @RequestBody(required = false) CreateAgentSessionRequest request
    ) {
        String rootPath = request != null ? request.rootPath() : null;
        AgentSession session = agentSessionStore.create(actor(authentication), rootPath);
        return session.toSummaryMap();
    }

    @GetMapping("/agent/sessions/{sessionId}")
    public Map<String, Object> getAgentSession(
            Authentication authentication,
            @PathVariable String sessionId
    ) {
        AgentSession session = agentSessionStore.require(sessionId, actor(authentication))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        return session.toMap();
    }

    @PostMapping("/agent/sessions/{sessionId}/messages")
    public Map<String, Object> sendAgentMessage(
            Authentication authentication,
            @PathVariable String sessionId,
            @Valid @RequestBody AgentMessageRequest request
    ) {
        AgentSession session = agentSessionStore.require(sessionId, actor(authentication))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        if (request.rootPath() != null && !request.rootPath().isBlank()) {
            session.setRootPath(request.rootPath());
        }
        try {
            return agentService.runTurn(
                    session,
                    request.message(),
                    authentication,
                    actor(authentication)
            );
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @DeleteMapping("/agent/sessions/{sessionId}")
    public Map<String, Object> deleteAgentSession(
            Authentication authentication,
            @PathVariable String sessionId
    ) {
        boolean deleted = agentSessionStore.delete(sessionId, actor(authentication));
        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
        }
        return Map.of("status", "OK", "sessionId", sessionId);
    }

    @PostMapping("/agent/run")
    public Map<String, Object> runAgent(
            Authentication authentication,
            @Valid @RequestBody AgentRunRequest request
    ) {
        try {
            return agentService.run(
                    request.goal(),
                    request.rootPath(),
                    authentication,
                    actor(authentication)
            );
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PostMapping("/bundles/generate")
    public Map<String, Object> generateBundle(
            Authentication authentication,
            @Valid @RequestBody GenerateBundleRequest request
    ) {
        try {
            return generationService.generate(
                    request.appId(),
                    request.prompt(),
                    request.baseManifest() != null
                            ? parseManifest(request.baseManifest())
                            : null,
                    actor(authentication)
            );
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private ApplicationBundleDeployService.BundleManifest parseManifest(Map<String, Object> raw) {
        try {
            return BundleManifestJsonSupport.parse(objectMapper, raw);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private static String actor(Authentication authentication) {
        return authentication != null ? authentication.getName() : "anonymous";
    }

    public record BundleToolRequest(
            @NotBlank String appId,
            @Valid Map<String, Object> manifest
    ) {
    }

    public record GenerateBundleRequest(
            @NotBlank String appId,
            @NotBlank String prompt,
            Map<String, Object> baseManifest
    ) {
    }

    public record AgentRunRequest(
            @NotBlank String goal,
            String rootPath
    ) {
    }

    public record CreateAgentSessionRequest(
            String rootPath
    ) {
    }

    public record AgentMessageRequest(
            @NotBlank String message,
            String rootPath
    ) {
    }
}
