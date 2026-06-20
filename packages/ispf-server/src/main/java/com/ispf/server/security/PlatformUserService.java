package com.ispf.server.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.server.config.IspfRoles;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.tenant.TenantStore;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PlatformUserService {

    public static final String SECURITY_ROOT = "root.platform.security";
    public static final String USERS_FOLDER = SECURITY_ROOT + ".users";
    public static final String ROLES_FOLDER = SECURITY_ROOT + ".roles";
    public static final String USERS_PATH_PREFIX = USERS_FOLDER + ".";
    public static final String ROLES_PATH_PREFIX = ROLES_FOLDER + ".";

    private final PlatformUserStore userStore;
    private final PlatformRoleService roleService;
    private final PlatformAuthSessionStore sessionStore;
    private final PlatformUserObjectTreeService objectTreeService;
    private final ObjectManager objectManager;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final TenantStore tenantStore;

    public PlatformUserService(
            PlatformUserStore userStore,
            PlatformRoleService roleService,
            PlatformAuthSessionStore sessionStore,
            PlatformUserObjectTreeService objectTreeService,
            ObjectManager objectManager,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper,
            TenantStore tenantStore
    ) {
        this.userStore = userStore;
        this.roleService = roleService;
        this.sessionStore = sessionStore;
        this.objectTreeService = objectTreeService;
        this.objectManager = objectManager;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.tenantStore = tenantStore;
    }

    @Transactional
    public void ensureDefaultUsers() {
        if (userStore.exists()) {
            objectTreeService.syncAll();
            return;
        }
        createUser("admin", "Administrator", "admin", List.of(IspfRoles.ADMIN));
        createUser("operator", "Operator", "operator", List.of(IspfRoles.OPERATOR));
        objectTreeService.syncAll();
    }

    @Transactional
    public Map<String, Object> login(String username, String password) {
        PlatformUserStore.PlatformUser user = userStore.findByUsername(username.trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));
        if (!user.enabled()) {
            throw new IllegalArgumentException("User is disabled");
        }
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }
        sessionStore.deleteExpired(Instant.now());
        String token = UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plusSeconds(12 * 3600);
        sessionStore.save(token, user.username(), expiresAt);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", token);
        response.put("expiresAt", expiresAt.toString());
        response.put("username", user.username());
        response.put("displayName", user.displayName());
        response.put("roles", deserializeRoles(user.rolesJson()));
        response.put("autoStartEnabled", user.autoStartEnabled());
        if (user.autoStartApp() != null && !user.autoStartApp().isBlank()) {
            response.put("autoStartApp", user.autoStartApp());
        }
        tenantStore.findTenantIdForUser(user.username()).ifPresent(tenantId -> response.put("tenantId", tenantId));
        return response;
    }

    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sessionStore.delete(token.trim());
        }
    }

    public Optional<AuthenticatedUser> authenticateToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return sessionStore.findValid(token.trim(), Instant.now())
                .flatMap(session -> userStore.findByUsername(session.username()))
                .filter(PlatformUserStore.PlatformUser::enabled)
                .map(user -> new AuthenticatedUser(
                        user.username(),
                        user.displayName(),
                        deserializeRoles(user.rolesJson())
                ));
    }

    @Transactional
    public Map<String, Object> createUser(
            String username,
            String displayName,
            String password,
            List<String> roles
    ) {
        String normalized = normalizeUsername(username);
        validateRoles(roles);
        if (userStore.findByUsername(normalized).isPresent()) {
            throw new IllegalArgumentException("User already exists: " + normalized);
        }
        String objectPath = USERS_PATH_PREFIX + normalized;
        PlatformUserStore.PlatformUser user = new PlatformUserStore.PlatformUser(
                normalized,
                passwordEncoder.encode(password),
                displayName != null && !displayName.isBlank() ? displayName : normalized,
                serializeRoles(roles),
                objectPath,
                true,
                false,
                null,
                Instant.now(),
                Instant.now()
        );
        userStore.upsert(user);
        objectTreeService.syncUser(user);
        return toSummary(user);
    }

    @Transactional
    public Map<String, Object> updateUser(
            String username,
            String displayName,
            List<String> roles,
            Boolean enabled,
            Boolean autoStartEnabled,
            String autoStartApp
    ) {
        PlatformUserStore.PlatformUser existing = userStore.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        List<String> resolvedRoles = roles != null ? roles : deserializeRoles(existing.rolesJson());
        validateRoles(resolvedRoles);
        boolean resolvedEnabled = enabled != null ? enabled : existing.enabled();
        String resolvedDisplayName = displayName != null && !displayName.isBlank()
                ? displayName
                : existing.displayName();
        boolean resolvedAutoStartEnabled = autoStartEnabled != null
                ? autoStartEnabled
                : existing.autoStartEnabled();
        String resolvedAutoStartApp = autoStartApp != null
                ? normalizeAutoStartApp(autoStartApp, resolvedAutoStartEnabled)
                : existing.autoStartApp();
        if (resolvedAutoStartEnabled && (resolvedAutoStartApp == null || resolvedAutoStartApp.isBlank())) {
            throw new IllegalArgumentException("autoStartApp is required when autoStartEnabled is true");
        }
        userStore.updateProfile(username, resolvedDisplayName, serializeRoles(resolvedRoles), resolvedEnabled);
        userStore.updateAutoStart(username, resolvedAutoStartEnabled, resolvedAutoStartApp);
        PlatformUserStore.PlatformUser updated = userStore.findByUsername(username).orElseThrow();
        objectTreeService.syncUser(updated);
        if (objectManager.tree().findByPath(existing.objectPath()).isPresent()) {
            objectManager.updateInfo(existing.objectPath(), resolvedDisplayName, "username=" + username);
        }
        return toSummary(updated);
    }

    @Transactional
    public void setPassword(String username, String password) {
        if (password == null || password.length() < 4) {
            throw new IllegalArgumentException("Password must be at least 4 characters");
        }
        userStore.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        userStore.updatePassword(username, passwordEncoder.encode(password));
    }

    @Transactional
    public void deleteUser(String username) {
        PlatformUserStore.PlatformUser user = userStore.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        if (IspfRoles.ADMIN.equals(username)) {
            long adminCount = userStore.listAll().stream()
                    .filter(PlatformUserStore.PlatformUser::enabled)
                    .filter(u -> deserializeRoles(u.rolesJson()).contains(IspfRoles.ADMIN))
                    .count();
            if (adminCount <= 1) {
                throw new IllegalArgumentException("Cannot delete the last enabled admin user");
            }
        }
        userStore.delete(username);
        if (objectManager.tree().findByPath(user.objectPath()).isPresent()) {
            objectManager.delete(user.objectPath());
        }
    }

    @Transactional
    public void syncVariableFromObject(String objectPath, String variableName, DataRecord value) {
        if (!objectPath.startsWith(USERS_PATH_PREFIX)) {
            return;
        }
        String username = objectPath.substring(USERS_PATH_PREFIX.length());
        PlatformUserStore.PlatformUser user = userStore.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        String fieldValue = readStringField(value);
        switch (variableName) {
            case "roles" -> {
                List<String> roles = parseRolesField(fieldValue);
                validateRoles(roles);
                userStore.updateProfile(user.username(), user.displayName(), serializeRoles(roles), user.enabled());
            }
            case "enabled" -> {
                boolean enabled = Boolean.parseBoolean(fieldValue);
                userStore.updateProfile(user.username(), user.displayName(), user.rolesJson(), enabled);
            }
            case "displayName" -> userStore.updateProfile(
                    user.username(),
                    fieldValue,
                    user.rolesJson(),
                    user.enabled()
            );
            case "autoStartEnabled" -> {
                boolean enabledAutoStart = Boolean.parseBoolean(fieldValue);
                String app = enabledAutoStart ? user.autoStartApp() : user.autoStartApp();
                if (enabledAutoStart && (app == null || app.isBlank())) {
                    throw new IllegalArgumentException("autoStartApp must be set before enabling auto start");
                }
                userStore.updateAutoStart(user.username(), enabledAutoStart, app);
            }
            case "autoStartApp" -> {
                String app = normalizeAutoStartApp(fieldValue, user.autoStartEnabled());
                userStore.updateAutoStart(user.username(), user.autoStartEnabled(), app);
            }
            default -> {
                return;
            }
        }
        objectTreeService.syncUser(userStore.findByUsername(username).orElseThrow());
    }

    public List<Map<String, Object>> listUsers() {
        return userStore.listAll().stream().map(this::toSummary).toList();
    }

    public boolean isSecurityUserPath(String path) {
        return path != null && path.startsWith(USERS_PATH_PREFIX);
    }

    public String usernameFromPath(String path) {
        return path.substring(USERS_PATH_PREFIX.length());
    }

    private Map<String, Object> toSummary(PlatformUserStore.PlatformUser user) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("username", user.username());
        summary.put("displayName", user.displayName());
        summary.put("roles", deserializeRoles(user.rolesJson()));
        summary.put("enabled", user.enabled());
        summary.put("autoStartEnabled", user.autoStartEnabled());
        if (user.autoStartApp() != null && !user.autoStartApp().isBlank()) {
            summary.put("autoStartApp", user.autoStartApp());
        }
        summary.put("objectPath", user.objectPath());
        tenantStore.findTenantIdForUser(user.username()).ifPresent(tenantId -> summary.put("tenantId", tenantId));
        summary.put("createdAt", user.createdAt().toString());
        summary.put("updatedAt", user.updatedAt().toString());
        return summary;
    }

    private static String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        String normalized = username.trim().toLowerCase();
        if (!normalized.matches("[a-z0-9._-]{2,64}")) {
            throw new IllegalArgumentException("Invalid username format");
        }
        return normalized;
    }

    private void validateRoles(List<String> roles) {
        roleService.validateRoleNames(roles);
    }

    private String serializeRoles(List<String> roles) {
        try {
            return objectMapper.writeValueAsString(roles);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize roles", ex);
        }
    }

    private List<String> deserializeRoles(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<String> parseRolesField(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("roles cannot be empty");
        }
        List<String> roles = new ArrayList<>();
        for (String part : value.split(",")) {
            String role = part.trim().toLowerCase();
            if (!role.isBlank()) {
                roles.add(role);
            }
        }
        validateRoles(roles);
        return roles;
    }

    private static String normalizeAutoStartApp(String autoStartApp, boolean autoStartEnabled) {
        if (autoStartApp == null) {
            return null;
        }
        String normalized = autoStartApp.trim();
        if (normalized.isEmpty()) {
            return autoStartEnabled ? null : null;
        }
        if (!normalized.matches("[a-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("Invalid autoStartApp format");
        }
        return normalized;
    }

    private static String readStringField(DataRecord value) {
        if (value == null || value.rows().isEmpty()) {
            return "";
        }
        Map<String, Object> row = value.rows().getFirst();
        if (row.containsKey("value")) {
            return String.valueOf(row.get("value"));
        }
        return row.values().stream().findFirst().map(String::valueOf).orElse("");
    }

    public record AuthenticatedUser(String username, String displayName, List<String> roles) {
    }
}
