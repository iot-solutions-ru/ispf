package com.ispf.server.api;

import com.ispf.server.security.PlatformUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final PlatformUserService userService;

    public AuthController(PlatformUserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request) {
        try {
            return userService.login(request.username(), request.password());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.getMessage(), ex);
        }
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        String token = extractBearerToken(request);
        userService.logout(token);
        return Map.of("status", "logged-out");
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Map.of("authenticated", false, "roles", List.of());
        }
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(auth -> auth.startsWith("ROLE_") ? auth.substring(5) : auth)
                .toList();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("authenticated", true);
        response.put("principal", authentication.getName());
        response.put("roles", roles);
        return response;
    }

    private static String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring("Bearer ".length()).trim();
        }
        return null;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }
}
