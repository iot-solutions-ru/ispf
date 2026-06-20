package com.ispf.core.object;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of the object tree. Production deployments will persist via PostgreSQL.
 */
public class ObjectTree {

    private final Map<String, PlatformObject> nodesByPath = new ConcurrentHashMap<>();
    private final Map<String, PlatformObject> nodesById = new ConcurrentHashMap<>();

    public ObjectTree() {
        PlatformObject root = new PlatformObject(
                "root",
                "root",
                ObjectType.ROOT,
                "ISPF Root",
                "IoT Solutions Platform Framework root object",
                null
        );
        register(root);
    }

    public PlatformObject register(PlatformObject node) {
        if (nodesByPath.putIfAbsent(node.path(), node) != null) {
            throw new IllegalArgumentException("Object already exists: " + node.path());
        }
        nodesById.put(node.id(), node);
        return node;
    }

    public Optional<PlatformObject> findByPath(String path) {
        return Optional.ofNullable(nodesByPath.get(path));
    }

    public Optional<PlatformObject> findById(String id) {
        return Optional.ofNullable(nodesById.get(id));
    }

    public List<PlatformObject> childrenOf(String parentPath) {
        String prefix = parentPath.endsWith(".") ? parentPath : parentPath + ".";
        List<PlatformObject> children = new ArrayList<>();
        for (PlatformObject node : nodesByPath.values()) {
            if (node.path().startsWith(prefix)) {
                String remainder = node.path().substring(prefix.length());
                if (!remainder.contains(".")) {
                    children.add(node);
                }
            }
        }
        children.sort(Comparator.comparingInt(PlatformObject::sortOrder)
                .thenComparing(PlatformObject::displayName, String.CASE_INSENSITIVE_ORDER));
        return children;
    }

    public List<PlatformObject> all() {
        return List.copyOf(nodesByPath.values());
    }

    public PlatformObject require(String path) {
        return findByPath(path).orElseThrow(() -> new ObjectNotFoundException(path));
    }

    public String resolveChildPath(String parentPath, String name) {
        return parentPath + "." + name;
    }

    public void delete(String path) {
        if ("root".equals(path)) {
            throw new IllegalArgumentException("Cannot delete root object");
        }
        List<String> toRemove = nodesByPath.keySet().stream()
                .filter(p -> p.equals(path) || p.startsWith(path + "."))
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .toList();
        if (toRemove.isEmpty()) {
            throw new ObjectNotFoundException(path);
        }
        for (String p : toRemove) {
            PlatformObject removed = nodesByPath.remove(p);
            if (removed != null) {
                nodesById.remove(removed.id());
            }
        }
    }
}
