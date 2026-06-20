package com.ispf.server.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.Variable;
import com.ispf.server.config.IspfRoles;
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
    private final ObjectMapper objectMapper;

    public PlatformUserObjectTreeService(
            ObjectManager objectManager,
            PlatformUserStore userStore,
            ObjectMapper objectMapper
    ) {
        this.objectManager = objectManager;
        this.userStore = userStore;
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
                ObjectType.CUSTOM,
                "Roles",
                "Platform RBAC roles",
                "security-folder-v1"
        );
        ensureNode(
                PlatformUserService.ROLES_FOLDER + "." + IspfRoles.ADMIN,
                ObjectType.CUSTOM,
                "admin",
                "Full platform administration",
                "platform-role-v1"
        );
        ensureNode(
                PlatformUserService.ROLES_FOLDER + "." + IspfRoles.OPERATOR,
                ObjectType.CUSTOM,
                "operator",
                "Operator HMI and read-only automation",
                "platform-role-v1"
        );
    }

    @Transactional
    public void syncUser(PlatformUserStore.PlatformUser user) {
        ensureSecurityRoot();
        ensureNode(
                PlatformUserService.USERS_FOLDER,
                ObjectType.CUSTOM,
                "Users",
                "Platform user accounts",
                "security-folder-v1"
        );
        String path = user.objectPath();
        ensureNode(
                path,
                ObjectType.USER,
                user.displayName(),
                "username=" + user.username(),
                "platform-user-v1"
        );
        setStringVariable(path, "username", user.username(), false);
        setStringVariable(path, "displayName", user.displayName(), true);
        setStringVariable(path, "roles", String.join(",", deserializeRoles(user.rolesJson())), true);
        setBooleanVariable(path, "enabled", user.enabled(), true);
        setBooleanVariable(path, "autoStartEnabled", user.autoStartEnabled(), true);
        setStringVariable(path, "autoStartApp", user.autoStartApp() != null ? user.autoStartApp() : "", true);
    }

    private void ensureSecurityRoot() {
        ensureNode(
                PlatformUserService.SECURITY_ROOT,
                ObjectType.CUSTOM,
                "Security",
                "Authentication and RBAC",
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

    private void ensureNode(
            String path,
            ObjectType type,
            String displayName,
            String description,
            String templateId
    ) {
        if (objectManager.tree().findByPath(path).isPresent()) {
            objectManager.updateInfo(path, displayName, description);
            return;
        }
        int lastDot = path.lastIndexOf('.');
        if (lastDot > 0) {
            String parentPath = path.substring(0, lastDot);
            if (objectManager.tree().findByPath(parentPath).isEmpty()) {
                ensureNode(
                        parentPath,
                        ObjectType.CUSTOM,
                        parentPath.substring(parentPath.lastIndexOf('.') + 1),
                        "",
                        "security-folder-v1"
                );
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
            node.addVariable(new Variable(name, schema, true, writable, null, record));
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
