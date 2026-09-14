package com.ispf.server.api;

import com.ispf.server.alert.AlertRule;
import com.ispf.server.alert.AlertRuleService;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;
import com.ispf.server.tenant.TenantVirtualRootService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/alert-rules")
public class AlertRuleController {

    private final AlertRuleService alertRuleService;
    private final TenantScopeService tenantScopeService;
    private final TenantVirtualRootService tenantVirtualRootService;
    private final ObjectAccessService objectAccessService;

    public AlertRuleController(
            AlertRuleService alertRuleService,
            TenantScopeService tenantScopeService,
            TenantVirtualRootService tenantVirtualRootService,
            ObjectAccessService objectAccessService
    ) {
        this.alertRuleService = alertRuleService;
        this.tenantScopeService = tenantScopeService;
        this.tenantVirtualRootService = tenantVirtualRootService;
        this.objectAccessService = objectAccessService;
    }

    @GetMapping
    public List<AlertRule> list(Authentication authentication) {
        return alertRuleService.list().stream()
                .filter(rule -> canReadPath(rule.id(), authentication))
                .toList();
    }

    @GetMapping("/by-path")
    public AlertRule get(@RequestParam String path, Authentication authentication) {
        return alertRuleService.get(requirePathRead(path, authentication));
    }

    @PostMapping
    public AlertRule create(
            @RequestBody AlertRuleService.CreateAlertRuleRequest request,
            Authentication authentication
    ) {
        requirePathWrite(AutomationTreeService.ALERT_RULES_ROOT, authentication);
        return alertRuleService.create(request);
    }

    @PutMapping("/by-path")
    public AlertRule update(
            @RequestParam String path,
            @RequestBody AlertRuleService.UpdateAlertRuleRequest request,
            Authentication authentication
    ) {
        return alertRuleService.update(requirePathWrite(path, authentication), request);
    }

    @DeleteMapping("/by-path")
    public void delete(@RequestParam String path, Authentication authentication) {
        alertRuleService.delete(requirePathWrite(path, authentication));
    }

    private boolean canReadPath(String path, Authentication authentication) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String canonical = tenantVirtualRootService.toCanonical(path, authentication);
        return tenantScopeService.isPathVisible(canonical, authentication)
                && objectAccessService.canRead(canonical, authentication);
    }

    private String requirePathRead(String path, Authentication authentication) {
        String canonical = requirePathAccess(path, authentication);
        objectAccessService.requireRead(canonical, authentication);
        return canonical;
    }

    private String requirePathWrite(String path, Authentication authentication) {
        String canonical = requirePathAccess(path, authentication);
        objectAccessService.requireWrite(canonical, authentication);
        return canonical;
    }

    private String requirePathAccess(String path, Authentication authentication) {
        String canonical = tenantVirtualRootService.toCanonical(path, authentication);
        tenantScopeService.requirePathInScope(canonical, authentication);
        return canonical;
    }
}
