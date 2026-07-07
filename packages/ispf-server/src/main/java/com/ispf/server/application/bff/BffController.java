package com.ispf.server.application.bff;

import tools.jackson.databind.ObjectMapper;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.server.application.function.ApplicationFunctionStore;
import com.ispf.server.function.FunctionInvokeAccessService;
import com.ispf.server.function.FunctionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        DataRecord output = functionService.invoke(
                request.objectPath(),
                request.functionName(),
                request.input()
        );
        return BffWireMapper.toWire(output, request.wireProfile(), resolveOutputSchema(
                request.objectPath(),
                request.functionName()
        ));
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
            DataRecord input,
            String wireProfile
    ) {
    }
}
