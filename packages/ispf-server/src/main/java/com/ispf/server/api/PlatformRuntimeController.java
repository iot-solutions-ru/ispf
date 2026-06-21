package com.ispf.server.api;

import com.ispf.server.application.function.FunctionInvokeAuditEntry;
import com.ispf.server.application.function.FunctionInvokeAuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformRuntimeController {

    private final FunctionInvokeAuditService auditService;

    public PlatformRuntimeController(FunctionInvokeAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/function-invocations")
    public List<FunctionInvokeAuditEntry> functionInvocations(
            @RequestParam(required = false) String objectPath,
            @RequestParam(required = false) String functionName,
            @RequestParam(required = false) Boolean success,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return auditService.list(objectPath, functionName, success, limit);
    }
}
