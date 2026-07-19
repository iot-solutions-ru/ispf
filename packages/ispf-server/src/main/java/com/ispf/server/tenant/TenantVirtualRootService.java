package com.ispf.server.tenant;

import com.ispf.server.api.dto.ObjectDto;
import com.ispf.server.api.dto.ObjectEditorDto;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Auth-aware sole-tenant path remapping at the API boundary.
 */
@Service
public class TenantVirtualRootService {

    private final TenantScopeService tenantScopeService;

    public TenantVirtualRootService(TenantScopeService tenantScopeService) {
        this.tenantScopeService = tenantScopeService;
    }

    public Optional<String> activeTenantId(Authentication authentication) {
        return tenantScopeService.resolveTenantId(authentication);
    }

    public boolean isActive(Authentication authentication) {
        return activeTenantId(authentication).isPresent();
    }

    public String toCanonical(String path, Authentication authentication) {
        return activeTenantId(authentication)
                .map(tenantId -> TenantVirtualRoot.toCanonical(path, tenantId))
                .orElse(path);
    }

    public String toVirtual(String path, Authentication authentication) {
        return activeTenantId(authentication)
                .map(tenantId -> TenantVirtualRoot.toVirtual(path, tenantId))
                .orElse(path);
    }

    public String toVirtual(String path, String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return path;
        }
        return TenantVirtualRoot.toVirtual(path, tenantId);
    }

    public String dataSourcesRoot(Authentication authentication) {
        return activeTenantId(authentication)
                .map(TenantVirtualRoot::canonicalDataSourcesRoot)
                .orElse(TenantVirtualRoot.VIRTUAL_PLATFORM + ".data-sources");
    }

    public ObjectDto virtualize(ObjectDto dto, Authentication authentication) {
        if (dto == null || !isActive(authentication)) {
            return dto;
        }
        String virtualPath = toVirtual(dto.path(), authentication);
        if (virtualPath == null) {
            return null;
        }
        String groupContext = dto.groupContextPath() == null
                ? null
                : toVirtual(dto.groupContextPath(), authentication);
        if (virtualPath.equals(dto.path())
                && (groupContext == null
                ? dto.groupContextPath() == null
                : groupContext.equals(dto.groupContextPath()))) {
            return dto;
        }
        return dto.withPaths(virtualPath, groupContext);
    }

    public List<ObjectDto> virtualize(List<ObjectDto> dtos, Authentication authentication) {
        if (dtos == null || !isActive(authentication)) {
            return dtos;
        }
        List<ObjectDto> out = new ArrayList<>(dtos.size());
        for (ObjectDto dto : dtos) {
            ObjectDto remapped = virtualize(dto, authentication);
            if (remapped != null) {
                out.add(remapped);
            }
        }
        return out;
    }

    public ObjectEditorDto virtualize(ObjectEditorDto editor, Authentication authentication) {
        if (editor == null || !isActive(authentication)) {
            return editor;
        }
        ObjectDto remapped = virtualize(editor.object(), authentication);
        if (remapped == null || remapped == editor.object()) {
            return editor;
        }
        return new ObjectEditorDto(
                remapped,
                editor.variables(),
                editor.events(),
                editor.functions()
        );
    }
}
