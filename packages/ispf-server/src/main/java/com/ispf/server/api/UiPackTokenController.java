package com.ispf.server.api;

import com.ispf.server.application.uipack.UiPackScopedTokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * ADR-0073: admin mint of a BFF-scoped bearer ({@code uiPackAppId}).
 * Packs must not call {@code POST /api/v1/auth/login} to obtain a console token.
 */
@RestController
@RequestMapping("/api/v1/security/ui-pack-tokens")
public class UiPackTokenController {

    private final UiPackScopedTokenService tokens;

    public UiPackTokenController(UiPackScopedTokenService tokens) {
        this.tokens = tokens;
    }

    @PostMapping
    public Map<String, Object> mint(
            @Valid @RequestBody MintUiPackTokenRequest request,
            Authentication authentication
    ) {
        return tokens.mint(request.username(), request.appId(), request.ttlHours(), authentication);
    }

    public record MintUiPackTokenRequest(
            @NotBlank String username,
            @NotBlank String appId,
            Integer ttlHours
    ) {
    }
}
