package com.ispf.server.api;

import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.correlator.EventCorrelator;
import com.ispf.server.correlator.EventCorrelatorService;
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
@RequestMapping("/api/v1/correlators")
public class EventCorrelatorController {

    private final EventCorrelatorService correlatorService;
    private final TenantScopeService tenantScopeService;
    private final TenantVirtualRootService tenantVirtualRootService;
    private final ObjectAccessService objectAccessService;

    public EventCorrelatorController(
            EventCorrelatorService correlatorService,
            TenantScopeService tenantScopeService,
            TenantVirtualRootService tenantVirtualRootService,
            ObjectAccessService objectAccessService
    ) {
        this.correlatorService = correlatorService;
        this.tenantScopeService = tenantScopeService;
        this.tenantVirtualRootService = tenantVirtualRootService;
        this.objectAccessService = objectAccessService;
    }

    @GetMapping
    public List<EventCorrelator> list(Authentication authentication) {
        return correlatorService.list().stream()
                .filter(correlator -> canReadPath(correlator.id(), authentication))
                .toList();
    }

    @GetMapping("/by-path")
    public EventCorrelator get(@RequestParam String path, Authentication authentication) {
        return correlatorService.get(requirePathRead(path, authentication));
    }

    @PostMapping
    public EventCorrelator create(
            @RequestBody EventCorrelatorService.CreateCorrelatorRequest request,
            Authentication authentication
    ) {
        requirePathWrite(AutomationTreeService.CORRELATORS_ROOT, authentication);
        return correlatorService.create(request);
    }

    @PutMapping("/by-path")
    public EventCorrelator update(
            @RequestParam String path,
            @RequestBody EventCorrelatorService.UpdateCorrelatorRequest request,
            Authentication authentication
    ) {
        return correlatorService.update(requirePathWrite(path, authentication), request);
    }

    @DeleteMapping("/by-path")
    public void delete(@RequestParam String path, Authentication authentication) {
        correlatorService.delete(requirePathWrite(path, authentication));
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
