package com.ispf.server.api;

import com.ispf.server.security.PlatformUserService;
import com.ispf.server.security.PlatformUserService;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.ispf.server.config.IspfRoles;

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
    public List<Map<String, Object>> list() {
        return userService.listUsers();
    }

    @PostMapping
    public Map<String, Object> create(@Valid @RequestBody CreateUserRequest request) {
        try {
            return userService.createUser(
                    request.username(),
                    request.displayName(),
                    request.password(),
                    request.roles() != null ? request.roles() : List.of(IspfRoles.OPERATOR)
            );
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PutMapping("/{username}")
    public Map<String, Object> update(
            @PathVariable String username,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        try {
            return userService.updateUser(
                    username,
                    request.displayName(),
                    request.roles(),
                    request.enabled()
            );
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PutMapping("/{username}/password")
    public Map<String, Object> setPassword(
            @PathVariable String username,
            @Valid @RequestBody SetPasswordRequest request
    ) {
        try {
            userService.setPassword(username, request.password());
            return Map.of("username", username, "status", "password-updated");
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @DeleteMapping("/{username}")
    public Map<String, Object> delete(@PathVariable String username) {
        try {
            userService.deleteUser(username);
            return Map.of("username", username, "status", "deleted");
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
            Boolean enabled
    ) {
    }

    public record SetPasswordRequest(@NotBlank String password) {
    }
}
