package com.ispf.server.application.bff;

import tools.jackson.databind.ObjectMapper;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.server.api.dto.DataRecordPayloadRequest;
import com.ispf.server.application.function.ApplicationFunctionStore;
import com.ispf.server.function.FunctionInvokeAccessService;
import com.ispf.server.function.FunctionService;
import com.ispf.server.security.UiPackScopedAccessFilter;
import com.ispf.server.security.acl.VariableAclRequestContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/bff")
public class BffController {

    private final FunctionService functionService;
    private final ApplicationFunctionStore functionStore;
    private final ObjectMapper objectMapper;
    private final FunctionInvokeAccessService invokeAccessService;

    public BffController(
            FunctionService functionService,
            ApplicationFunctionStore functionStore,
            ObjectMapper objectMapper,
            FunctionInvokeAccessService invokeAccessService
    ) {
        this.functionService = functionService;
        this.functionStore = functionStore;
        this.objectMapper = objectMapper;
        this.invokeAccessService = invokeAccessService;
    }

    @PostMapping("/invoke")
    public Map<String, Object> invoke(@RequestBody BffInvokeRequest request, Authentication authentication) {
        invokeAccessService.requireDirectInvoke(request.objectPath(), request.functionName(), authentication);
        requireUiPackOwnsFunction(request.objectPath(), request.functionName(), authentication);
        DataRecord output = VariableAclRequestContext.callAsMember(
                authentication,
                () -> functionService.invoke(
                        request.objectPath(),
                        request.functionName(),
                        request.input()
                )
        );
        return BffWireMapper.toWire(output, request.wireProfile(), resolveOutputSchema(
                request.objectPath(),
                request.functionName()
        ));
    }

    /**
     * ADR-0073: a principal with {@code uiPackAppId} may only invoke deployed functions of that app.
     * Referer/header checks in {@link com.ispf.server.config.IspfAuthorizationRules} remain an extra
     * gate and do not replace ownership of {@code objectPath}/{@code functionName}.
     */
    private void requireUiPackOwnsFunction(String objectPath, String functionName, Authentication authentication) {
        if (!UiPackScopedAccessFilter.isUiPackScoped(authentication)) {
            return;
        }
        String packAppId = UiPackScopedAccessFilter.uiPackAppId(authentication);
        if (packAppId == null || packAppId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "UI pack scoped token is missing uiPackAppId"
            );
        }
        String functionAppId = functionStore.findLatest(objectPath, functionName)
                .map(deployed -> deployed.appId())
                .orElse(null);
        if (functionAppId == null || !packAppId.equals(functionAppId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "UI pack token may only invoke functions owned by app " + packAppId
            );
        }
    }

    private DataSchema resolveOutputSchema(String objectPath, String functionName) {
        return functionStore.findLatest(objectPath, functionName)
                .map(deployed -> {
                    try {
                        return objectMapper.readValue(deployed.outputSchemaJson(), DataSchema.class);
                    } catch (Exception ex) {
                        throw new IllegalStateException("Invalid function schema JSON", ex);
                    }
                })
                .orElse(null);
    }

    public record BffInvokeRequest(
            String objectPath,
            String functionName,
            DataRecordPayloadRequest input,
            String wireProfile
    ) {
    }
}
