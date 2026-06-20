package com.ispf.server.api;

import com.ispf.server.tenant.Tenant;
import com.ispf.server.tenant.TenantDraft;
import com.ispf.server.tenant.TenantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping
    public List<TenantDto> list() {
        return tenantService.listTenants().stream().map(TenantDto::from).toList();
    }

    @PostMapping
    public TenantDto create(@Valid @RequestBody CreateTenantRequest request) {
        Tenant tenant = tenantService.createTenant(new TenantDraft(
                request.tenantId().trim().toLowerCase(),
                request.displayName().trim(),
                request.enabled() == null || request.enabled()
        ));
        return TenantDto.from(tenant);
    }

    @DeleteMapping("/{tenantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String tenantId) {
        tenantService.deleteTenant(tenantId.trim().toLowerCase());
    }

    @PutMapping("/{tenantId}/users/{username}")
    public Map<String, String> assignUser(
            @PathVariable String tenantId,
            @PathVariable String username
    ) {
        tenantService.assignUserToTenant(username.trim().toLowerCase(), tenantId.trim().toLowerCase());
        return Map.of("username", username, "tenantId", tenantId);
    }

    @DeleteMapping("/{tenantId}/users/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassignUser(@PathVariable String tenantId, @PathVariable String username) {
        tenantService.clearUserTenant(username.trim().toLowerCase());
    }

    public record TenantDto(
            String tenantId,
            String displayName,
            boolean enabled,
            String objectPath,
            String platformPath
    ) {
        static TenantDto from(Tenant tenant) {
            return new TenantDto(
                    tenant.tenantId(),
                    tenant.displayName(),
                    tenant.enabled(),
                    tenant.objectPath(),
                    tenant.platformPath()
            );
        }
    }

    public record CreateTenantRequest(
            @NotBlank String tenantId,
            @NotBlank String displayName,
            Boolean enabled
    ) {
    }
}
