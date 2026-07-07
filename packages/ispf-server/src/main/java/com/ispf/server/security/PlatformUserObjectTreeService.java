package com.ispf.server.security;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.Variable;
import com.ispf.server.bootstrap.SystemObjectCatalogSupport;
import com.ispf.server.bootstrap.SystemObjectDescriptions;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class PlatformUserObjectTreeService {

    private final ObjectManager objectManager;
    private final PlatformUserStore userStore;
    private final PlatformRoleStore roleStore;
    private final ObjectMapper objectMapper;

    public PlatformUserObjectTreeService(
            ObjectManager objectManager,
            PlatformUserStore userStore,
            PlatformRoleStore roleStore,
            ObjectMapper objectMapper
    ) {
        this.objectManager = objectManager;
        this.userStore = userStore;
        this.roleStore = roleStore;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void syncAll() {
        ensureSecurityRoot();
        syncRoles();
        Set<String> expected = new HashSet<>();
        for (PlatformUserStore.PlatformUser user : userStore.listAll()) {
            syncUser(user);
            expected.add(user.username());
        }
        pruneUsers(expected);
    }

    @Transactional
    public void syncRoles() {
        ensureSecurityRoot();
        ensureNode(
                PlatformUserService.ROLES_FOLDER,
                ObjectType.ROLES,
                "security-folder-v1"
        );
        Set<String> expected = new HashSet<>();
        for (PlatformRoleStore.PlatformRole role : roleStore.listAll()) {
            syncRole(role);
            expected.add(role.name());
        }
        pruneRoles(expected);
    }

    @Transactional
    public void syncRole(PlatformRoleStore.PlatformRole role) {
        ensureSecurityRoot();
        ensureNode(
                PlatformUserService.ROLES_FOLDER,
                ObjectType.ROLES,
                "security-folder-v1"
        );
        ensureEntityNode(
                role.objectPath(),
                ObjectType.ROLE,
                role.displayName(),
                role.description(),
                "platform-role-v1"
        );
        setStringVariable(role.objectPath(), "description", role.description(), true);
    }

    private void pruneRoles(Set<String> expectedRoleNames) {
        if (objectManager.tree().findByPath(PlatformUserService.ROLES_FOLDER).isEmpty()) {
            return;
        }
        objectManager.tree().childrenOf(PlatformUserService.ROLES_FOLDER).forEach(child -> {
            String roleName = child.path().substring(child.path().lastIndexOf('.') + 1);
            if (!expectedRoleNames.contains(roleName)) {
                objectManager.delete(child.path());
            }
        });
    }

    @Transactional
    public void syncUser(PlatformUserStore.PlatformUser user) {
        ensureSecurityRoot();
        ensureNode(
                PlatformUserService.USERS_FOLDER,
                ObjectType.USERS,
                "security-folder-v1"
        );
        String path = user.objectPath();
        ensureEntityNode(
                path,
                ObjectType.USER,
                user.displayName(),
                "Platform login «" + user.username() + "». Roles and auto-start operator app are edited via Security → Users or the object variables.",
                "platform-user-v1"
        );
        setStringVariable(path, "username", user.username(), false);
        setStringVariable(path, "displayName", user.displayName(), true);
        setStringVariable(path, "roles", String.join(",", deserializeRoles(user.rolesJson())), true);
        setBooleanVariable(path, "enabled", user.enabled(), true);
        setBooleanVariable(path, "autoStartEnabled", user.autoStartEnabled(), true);
        setStringVariable(path, "autoStartApp", user.autoStartApp() != null ? user.autoStartApp() : "", true);
        setStringVariable(
                path,
                "timeZone",
                com.ispf.server.platform.time.PlatformTimeZones.normalizeOrDefault(user.timeZone()),
                true
        );
    }

    private void ensureSecurityRoot() {
        SystemObjectCatalogSupport.ensureFolder(
                objectManager,
                PlatformUserService.SECURITY_ROOT,
                ObjectType.SECURITY,
                "security-folder-v1"
        );
    }

    private void pruneUsers(Set<String> expectedUsernames) {
        if (objectManager.tree().findByPath(PlatformUserService.USERS_FOLDER).isEmpty()) {
            return;
        }
        objectManager.tree().childrenOf(PlatformUserService.USERS_FOLDER).forEach(child -> {
            String username = child.path().substring(child.path().lastIndexOf('.') + 1);
            if (!expectedUsernames.contains(username)) {
                objectManager.delete(child.path());
            }
        });
    }

    private void ensureEntityNode(
            String path,
            ObjectType type,
            String displayName,
            String description,
            String templateId
    ) {
        if (objectManager.tree().findByPath(path).isPresent()) {
            objectManager.updateInfo(path, displayName, description);
            objectManager.reconcileType(path, type);
            return;
        }
        int lastDot = path.lastIndexOf('.');
        if (lastDot > 0) {
            String parentPath = path.substring(0, lastDot);
            if (objectManager.tree().findByPath(parentPath).isEmpty()) {
                ensureSecurityRoot();
                if (parentPath.equals(PlatformUserService.USERS_FOLDER)) {
                    ensureNode(PlatformUserService.USERS_FOLDER, ObjectType.USERS, "security-folder-v1");
                } else if (parentPath.equals(PlatformUserService.ROLES_FOLDER)) {
                    ensureNode(PlatformUserService.ROLES_FOLDER, ObjectType.ROLES, "platform-role-v1");
                }
            }
        }
        objectManager.create(
                lastDot > 0 ? path.substring(0, lastDot) : "",
                path.substring(lastDot + 1),
                type,
                displayName,
                description,
                templateId
        );
    }

    private void ensureNode(
            String path,
            ObjectType type,
            String templateId
    ) {
        SystemObjectDescriptions.Entry entry = SystemObjectDescriptions.resolve(path)
                .orElseThrow(() -> new IllegalStateException("Missing system description: " + path));
        if (objectManager.tree().findByPath(path).isPresent()) {
            objectManager.updateInfo(path, entry.displayName(), entry.description());
            objectManager.reconcileType(path, type);
            return;
        }
        int lastDot = path.lastIndexOf('.');
        if (lastDot > 0) {
            String parentPath = path.substring(0, lastDot);
            if (objectManager.tree().findByPath(parentPath).isEmpty()) {
                ensureSecurityRoot();
            }
        }
        objectManager.create(
                lastDot > 0 ? path.substring(0, lastDot) : "",
                path.substring(lastDot + 1),
                type,
                entry.displayName(),
                entry.description(),
                templateId
        );
    }

    private void setStringVariable(String path, String name, String value, boolean writable) {
        DataSchema schema = DataSchema.builder(name).field("value", FieldType.STRING).build();
        DataRecord record = DataRecord.single(schema, java.util.Map.of("value", value));
        upsertVariable(path, name, schema, record, writable);
    }

    private void setBooleanVariable(String path, String name, boolean value, boolean writable) {
        DataSchema schema = DataSchema.builder(name).field("value", FieldType.BOOLEAN).build();
        DataRecord record = DataRecord.single(schema, java.util.Map.of("value", value));
        upsertVariable(path, name, schema, record, writable);
    }

    private void upsertVariable(
            String path,
            String name,
            DataSchema schema,
            DataRecord record,
            boolean writable
    ) {
        if (objectManager.tree().findByPath(path).isEmpty()) {
            return;
        }
        var node = objectManager.require(path);
        if (node.getVariable(name).isEmpty()) {
            node.addVariable(new Variable(name, schema, true, writable, record));
            objectManager.persistNodeTree(path);
            return;
        }
        if (writable) {
            objectManager.setVariableValue(path, name, record);
        } else {
            objectManager.setSystemVariableValue(path, name, record);
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
}
