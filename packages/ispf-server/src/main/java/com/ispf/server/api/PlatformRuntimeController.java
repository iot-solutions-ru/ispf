package com.ispf.server.api;

import com.ispf.server.application.function.FunctionInvokeAuditEntry;
import com.ispf.server.application.function.FunctionInvokeAuditService;
import com.ispf.server.binding.BindingInvokeAuditEntry;
import com.ispf.server.binding.BindingInvokeAuditService;
import com.ispf.server.object.ObjectManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformRuntimeController {

    private final FunctionInvokeAuditService functionAuditService;
    private final BindingInvokeAuditService bindingAuditService;
    private final ObjectManager objectManager;

    public PlatformRuntimeController(
            FunctionInvokeAuditService functionAuditService,
            BindingInvokeAuditService bindingAuditService,
            ObjectManager objectManager
    ) {
        this.functionAuditService = functionAuditService;
        this.bindingAuditService = bindingAuditService;
        this.objectManager = objectManager;
    }

    @GetMapping("/function-invocations")
    public List<FunctionInvokeAuditEntry> functionInvocations(
            @RequestParam(required = false) String objectPath,
            @RequestParam(required = false) String functionName,
            @RequestParam(required = false) Boolean success,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return functionAuditService.list(objectPath, functionName, success, limit);
    }

    @GetMapping("/binding-invocations")
    public List<BindingInvokeAuditEntry> bindingInvocations(
            @RequestParam(required = false) String objectPath,
            @RequestParam(required = false) String bindingKind,
            @RequestParam(required = false) String ruleId,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) Boolean changed,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return bindingAuditService.list(objectPath, bindingKind, ruleId, success, changed, limit);
    }

    @GetMapping("/function-audit-status")
    public Map<String, Object> functionAuditStatus(
            @RequestParam(required = false) String objectPath
    ) {
        requireObjectIfPresent(objectPath);
        boolean masterEnabled = functionAuditService.isMasterEnabled();
        boolean objectEnabled = objectPath != null && !objectPath.isBlank()
                && functionAuditService.isObjectAuditEnabled(objectPath.trim());
        return Map.of(
                "masterEnabled", masterEnabled,
                "objectEnabled", objectEnabled,
                "mode", functionAuditService.auditMode().name().toLowerCase(Locale.ROOT),
                "enabled", objectPath != null && !objectPath.isBlank()
                        ? objectEnabled
                        : masterEnabled
        );
    }

    @GetMapping("/binding-audit-status")
    public Map<String, Object> bindingAuditStatus(
            @RequestParam(required = false) String objectPath
    ) {
        requireObjectIfPresent(objectPath);
        boolean masterEnabled = bindingAuditService.isMasterEnabled();
        boolean objectEnabled = objectPath != null && !objectPath.isBlank()
                && bindingAuditService.isObjectAuditEnabled(objectPath.trim());
        return Map.of(
                "masterEnabled", masterEnabled,
                "objectEnabled", objectEnabled,
                "enabled", objectPath != null && !objectPath.isBlank()
                        ? objectEnabled
                        : masterEnabled
        );
    }

    private void requireObjectIfPresent(String objectPath) {
        if (objectPath != null && !objectPath.isBlank()) {
            objectManager.require(objectPath.trim());
        }
    }
}
