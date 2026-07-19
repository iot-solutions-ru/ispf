package com.ispf.server.api;

import com.ispf.server.config.IspfRoles;
import com.ispf.server.security.PlatformUserService;
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
@RequestMapping("/api/v1/security/users")
public class SecurityUserController {

    private final PlatformUserService userService;

    public SecurityUserController(PlatformUserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<Map<String, Object>> list(Authentication authentication) {
        return userService.listUsers(authentication);
    }

    @PostMapping
    public Map<String, Object> create(
            @Valid @RequestBody CreateUserRequest request,
            Authentication authentication
    ) {
        try {
            return userService.createUser(
                    request.username(),
                    request.displayName(),
                    request.password(),
                    request.roles() != null ? request.roles() : List.of(IspfRoles.OPERATOR),
                    authentication
            );
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PutMapping("/{username}")
    public Map<String, Object> update(
            @PathVariable String username,
            @Valid @RequestBody UpdateUserRequest request,
            Authentication authentication
    ) {
        try {
            return userService.updateUser(
                    username,
                    request.displayName(),
                    request.roles(),
                    request.enabled(),
                    request.autoStartEnabled(),
                    request.autoStartApp(),
                    request.timeZone(),
                    authentication
            );
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PutMapping("/{username}/password")
    public Map<String, Object> setPassword(
            @PathVariable String username,
            @Valid @RequestBody SetPasswordRequest request,
            Authentication authentication
    ) {
        try {
            userService.setPassword(username, request.password(), authentication);
            return Map.of("username", username, "status", "password-updated");
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @DeleteMapping("/{username}")
    public Map<String, Object> delete(@PathVariable String username, Authentication authentication) {
        try {
            userService.deleteUser(username, authentication);
            return Map.of("username", username, "status", "deleted");
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PostMapping("/{username}/federation-token")
    public Map<String, Object> issueFederationToken(
            @PathVariable String username,
            @RequestBody(required = false) IssueFederationTokenRequest request,
            Authentication authentication
    ) {
        try {
            Integer ttlHours = request != null ? request.ttlHours() : null;
            return userService.issueFederationToken(username, ttlHours, authentication);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    public record CreateUserRequest(
            @NotBlank String username,
            String displayName,
            @NotBlank String password,
            List<String> roles
    ) {
    }

    public record UpdateUserRequest(
            String displayName,
            List<String> roles,
            Boolean enabled,
            Boolean autoStartEnabled,
            String autoStartApp,
            String timeZone
    ) {
    }

    public record SetPasswordRequest(@NotBlank String password) {
    }

    public record IssueFederationTokenRequest(Integer ttlHours) {
    }
}
