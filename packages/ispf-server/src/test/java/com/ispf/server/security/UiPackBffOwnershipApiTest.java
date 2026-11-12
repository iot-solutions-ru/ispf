package com.ispf.server.security;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.server.api.dto.DataRecordPayloadRequest;
import com.ispf.server.application.bff.BffController;
import com.ispf.server.application.function.ApplicationFunctionHandler;
import com.ispf.server.application.function.ApplicationFunctionStore;
import com.ispf.server.function.FunctionInvokeAccessService;
import com.ispf.server.function.FunctionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UiPackBffOwnershipApiTest {

    private static final String PATH = "root.platform.singleton-blueprints.hub";

    @Mock
    private FunctionService functionService;
    @Mock
    private ApplicationFunctionStore functionStore;
    @Mock
    private FunctionInvokeAccessService invokeAccessService;

    private BffController controller;

    @BeforeEach
    void setUp() {
        controller = new BffController(functionService, functionStore, new ObjectMapper(), invokeAccessService);
        doNothing().when(invokeAccessService).requireDirectInvoke(any(), any(), any());
    }

    @Test
    void packScopedTokenCannotInvokeForeignAppFunctionEvenWithMatchingPrincipal() {
        when(functionStore.findLatest(PATH, "foreign_fn")).thenReturn(Optional.of(deployed("other-app", "foreign_fn")));

        assertThatThrownBy(() -> controller.invoke(
                new BffController.BffInvokeRequest(PATH, "foreign_fn", null, null),
                packAuth("partner-app")
        )).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403")
                .hasMessageContaining("partner-app");
    }

    @Test
    void packScopedTokenMayInvokeOwnAppFunction() {
        when(functionStore.findLatest(PATH, "own_fn")).thenReturn(Optional.of(deployed("partner-app", "own_fn")));
        when(functionService.invoke(eq(PATH), eq("own_fn"), nullable(DataRecordPayloadRequest.class)))
                .thenReturn(DataRecord.empty(DataSchema.builder("out").build()));

        controller.invoke(
                new BffController.BffInvokeRequest(PATH, "own_fn", null, null),
                packAuth("partner-app")
        );

        verify(functionService).invoke(eq(PATH), eq("own_fn"), isNull(DataRecordPayloadRequest.class));
    }

    @Test
    void packScopedTokenRejectsMissingFunctionOwnership() {
        when(functionStore.findLatest(PATH, "missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.invoke(
                new BffController.BffInvokeRequest(PATH, "missing", null, null),
                packAuth("partner-app")
        )).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void roleUiPackWithoutAppIdIsRejected() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .subject("admin")
                .build();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                jwt,
                null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                        UiPackScopedAuthority.ROLE
                ))
        );

        assertThatThrownBy(() -> controller.invoke(
                new BffController.BffInvokeRequest(PATH, "own_fn", null, null),
                auth
        )).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("uiPackAppId");
    }

    private static ApplicationFunctionHandler.DeployedFunction deployed(String appId, String name) {
        return new ApplicationFunctionHandler.DeployedFunction(
                UUID.randomUUID(),
                appId,
                PATH,
                name,
                "1.0.0",
                "script",
                "{\"steps\":[{\"type\":\"return\",\"fields\":{\"ok\":true}}]}",
                "{\"name\":\"in\",\"fields\":[]}",
                "{\"name\":\"out\",\"fields\":[]}"
        );
    }

    private static Authentication packAuth(String appId) {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .claim(UiPackScopedAuthority.CLAIM, appId)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .subject("admin")
                .build();
        return new UsernamePasswordAuthenticationToken(jwt, null, List.of());
    }
}
