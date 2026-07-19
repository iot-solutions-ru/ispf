package com.ispf.server.workflow;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps webhookSlug → workflow path for ACTIVE workflows.
 */
@Service
public class WorkflowWebhookIndex {

    private final ObjectManager objectManager;
    private final ConcurrentHashMap<String, String> slugToPath = new ConcurrentHashMap<>();

    public WorkflowWebhookIndex(ObjectManager objectManager) {
        this.objectManager = objectManager;
    }

    public void rebuild() {
        slugToPath.clear();
        try {
            for (PlatformObject child : objectManager.tree().childrenOf("root.platform.workflows")) {
                indexNode(child);
            }
        } catch (Exception ignored) {
            // folder may not exist yet
        }
    }

    public void indexPath(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        slugToPath.entrySet().removeIf(entry -> path.equals(entry.getValue()));
        try {
            indexNode(objectManager.require(path));
        } catch (Exception ignored) {
            // ignore
        }
    }

    public Optional<String> resolve(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        String path = slugToPath.get(slug.trim());
        if (path != null) {
            return Optional.of(path);
        }
        rebuild();
        return Optional.ofNullable(slugToPath.get(slug.trim()));
    }

    private void indexNode(PlatformObject node) {
        if (node.type() != ObjectType.WORKFLOW) {
            return;
        }
        String slug = readString(node, "webhookSlug").orElse("");
        if (slug.isBlank()) {
            return;
        }
        String status = readString(node, "status").orElse("DRAFT");
        if (!"ACTIVE".equalsIgnoreCase(status)) {
            return;
        }
        slugToPath.put(slug, node.path());
    }

    private static Optional<String> readString(PlatformObject node, String variableName) {
        return node.getVariable(variableName)
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString)
                .filter(value -> !value.isBlank());
    }
}
