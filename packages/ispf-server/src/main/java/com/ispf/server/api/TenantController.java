package com.ispf.server.api;

import com.ispf.server.config.TenantIsolationProperties;
import com.ispf.server.tenant.Tenant;
import com.ispf.server.tenant.TenantDraft;
import com.ispf.server.tenant.TenantIsolationValidator;
import com.ispf.server.tenant.TenantQuotas;
import com.ispf.server.tenant.TenantQuotaService;
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
    private final TenantIsolationProperties tenantIsolationProperties;
    private final TenantIsolationValidator tenantIsolationValidator;

    public TenantController(
            TenantService tenantService,
            TenantIsolationProperties tenantIsolationProperties,
            TenantIsolationValidator tenantIsolationValidator
    ) {
        this.tenantService = tenantService;
        this.tenantIsolationProperties = tenantIsolationProperties;
        this.tenantIsolationValidator = tenantIsolationValidator;
    }

    @GetMapping
    public List<TenantDto> list() {
        return tenantService.listTenants().stream()
                .map(tenant -> TenantDto.from(tenant, tenantService.usage(tenant.tenantId()), null))
                .toList();
    }

    @PostMapping
    public TenantDto create(@Valid @RequestBody CreateTenantRequest request) {
        String tenantId = request.tenantId().trim().toLowerCase();
        tenantIsolationValidator.validateTenantIdForCreate(tenantId);
        Tenant tenant = tenantService.createTenant(new TenantDraft(
                tenantId,
                request.displayName().trim(),
                request.enabled() == null || request.enabled()
        ));
        String schemaName = tenantIsolationProperties.isHardMode()
                ? tenantIsolationValidator.resolveSchemaName(tenantId)
                : null;
        return TenantDto.from(tenant, tenantService.usage(tenant.tenantId()), schemaName);
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

    @PutMapping("/{tenantId}/quotas")
    public TenantDto updateQuotas(
            @PathVariable String tenantId,
            @Valid @RequestBody UpdateTenantQuotasRequest request
    ) {
        Tenant tenant = tenantService.updateQuotas(
                tenantId.trim().toLowerCase(),
                new TenantQuotas(request.maxDevices(), request.maxObjects())
        );
        return TenantDto.from(tenant, tenantService.usage(tenant.tenantId()), null);
    }

    @GetMapping("/{tenantId}/usage")
    public TenantUsageDto usage(@PathVariable String tenantId) {
        TenantQuotaService.TenantUsage usage = tenantService.usage(tenantId.trim().toLowerCase());
        return new TenantUsageDto(usage.tenantId(), usage.devices(), usage.objects());
    }

    public record TenantDto(
            String tenantId,
            String displayName,
            boolean enabled,
            String objectPath,
            String platformPath,
            Integer maxDevices,
            Integer maxObjects,
            Integer deviceCount,
            Integer objectCount,
            String schemaName
    ) {
        static TenantDto from(Tenant tenant, TenantQuotaService.TenantUsage usage, String schemaName) {
            return new TenantDto(
                    tenant.tenantId(),
                    tenant.displayName(),
                    tenant.enabled(),
                    tenant.objectPath(),
                    tenant.platformPath(),
                    tenant.maxDevices(),
                    tenant.maxObjects(),
                    usage.devices(),
                    usage.objects(),
                    schemaName
            );
        }
    }

    public record TenantUsageDto(String tenantId, int devices, int objects) {
    }

    public record UpdateTenantQuotasRequest(Integer maxDevices, Integer maxObjects) {
    }

    public record CreateTenantRequest(
            @NotBlank String tenantId,
            @NotBlank String displayName,
            Boolean enabled
    ) {
    }
}
