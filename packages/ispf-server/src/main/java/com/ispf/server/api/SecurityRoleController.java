package com.ispf.server.api;

import com.ispf.server.security.PlatformRoleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/security/roles")
public class SecurityRoleController {

    private final PlatformRoleService roleService;

    public SecurityRoleController(PlatformRoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    public List<Map<String, Object>> list(Authentication authentication) {
        return roleService.listRoles(authentication);
    }

    @PostMapping
    public Map<String, Object> create(
            @Valid @RequestBody CreateRoleRequest request,
            Authentication authentication
    ) {
        try {
            return roleService.createRole(
                    request.name(),
                    request.displayName(),
                    request.description(),
                    authentication
            );
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PutMapping("/{name}")
    public Map<String, Object> update(
            @PathVariable String name,
            @Valid @RequestBody UpdateRoleRequest request,
            Authentication authentication
    ) {
        try {
            return roleService.updateRole(name, request.displayName(), request.description(), authentication);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @DeleteMapping("/{name}")
    public Map<String, Object> delete(@PathVariable String name, Authentication authentication) {
        try {
            roleService.deleteRole(name, authentication);
            return Map.of("name", name, "status", "deleted");
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    public record CreateRoleRequest(
            @NotBlank String name,
            String displayName,
            String description
    ) {
    }

    public record UpdateRoleRequest(
            String displayName,
            String description
    ) {
    }
}
