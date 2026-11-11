package com.ispf.core.object;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * In-memory implementation of the object tree. Production deployments will persist via PostgreSQL.
 */
public class ObjectTree {

    private final NavigableMap<String, PlatformObject> nodesByPath = new ConcurrentSkipListMap<>();
    private final Map<String, PlatformObject> nodesById = new ConcurrentHashMap<>();
    /** Direct children by parent path — avoids O(n) scan on every childrenOf(). */
    private volatile Map<String, Set<String>> childPathsByParent = new ConcurrentHashMap<>();
    /** Secondary index by {@link ObjectType} for fast type-scoped lookups without scanning the whole tree. */
    private volatile Map<ObjectType, Set<String>> pathsByType = new ConcurrentHashMap<>();

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
        PlatformObject previousById = nodesById.putIfAbsent(node.id(), node);
        if (previousById != null) {
            nodesByPath.remove(node.path(), node);
            throw new IllegalArgumentException(
                    "Object id already registered: " + node.id() + " (existing path " + previousById.path() + ")"
            );
        }
        parentPathOf(node.path()).ifPresent(parent ->
                childPathsByParent.computeIfAbsent(parent, ignored -> ConcurrentHashMap.newKeySet()).add(node.path())
        );
        indexType(node.path(), node.type());
        return node;
    }

    public void updateNodeType(String path, ObjectType newType) {
        if (newType == null) {
            throw new IllegalArgumentException("type is required");
        }
        PlatformObject node = require(path);
        synchronized (node) {
            ObjectType previous = node.type();
            if (previous == newType) {
                indexType(path, newType);
                return;
            }
            node.setType(newType);
            unindexType(path, previous);
            indexType(path, newType);
        }
    }

    private void indexType(String path, ObjectType type) {
        if (path != null && type != null) {
            pathsByType.computeIfAbsent(type, ignored -> ConcurrentHashMap.newKeySet()).add(path);
        }
    }

    private void unindexType(String path, ObjectType type) {
        if (path == null || type == null) {
            return;
        }
        Set<String> paths = pathsByType.get(type);
        if (paths != null) {
            paths.remove(path);
        }
    }

    public Optional<PlatformObject> findByPath(String path) {
        return Optional.ofNullable(nodesByPath.get(path));
    }

    public Optional<PlatformObject> findById(String id) {
        return Optional.ofNullable(nodesById.get(id));
    }

    /** Ensures {@code childPath} appears under its parent in {@link #childrenOf(String)}. Idempotent. */
    public void ensureParentLink(String childPath) {
        if (childPath == null || childPath.isBlank() || "root".equals(childPath)) {
            return;
        }
        if (nodesByPath.get(childPath) == null) {
            return;
        }
        parentPathOf(childPath).ifPresent(parent ->
                childPathsByParent.computeIfAbsent(parent, ignored -> ConcurrentHashMap.newKeySet()).add(childPath)
        );
    }

    /** Rebuilds the parent→children and type indexes from registered nodes (startup / repair). */
    public void rebuildChildIndex() {
        Map<String, Set<String>> rebuiltChildren = new ConcurrentHashMap<>();
        Map<ObjectType, Set<String>> rebuiltTypes = new ConcurrentHashMap<>();
        for (PlatformObject node : nodesByPath.values()) {
            String nodePath = node.path();
            if (nodePath != null && !nodePath.isBlank() && !"root".equals(nodePath)) {
                parentPathOf(nodePath).ifPresent(parent ->
                        rebuiltChildren.computeIfAbsent(parent, ignored -> ConcurrentHashMap.newKeySet()).add(nodePath)
                );
            }
            ObjectType nodeType = node.type();
            if (nodePath != null && nodeType != null) {
                rebuiltTypes.computeIfAbsent(nodeType, ignored -> ConcurrentHashMap.newKeySet()).add(nodePath);
            }
        }
        childPathsByParent = rebuiltChildren;
        pathsByType = rebuiltTypes;
    }

    public List<PlatformObject> childrenOf(String parentPath) {
        Set<String> childPaths = childPathsByParent.get(parentPath);
        if (childPaths == null || childPaths.isEmpty()) {
            return List.of();
        }
        List<PlatformObject> children = new ArrayList<>(childPaths.size());
        for (String path : childPaths) {
            PlatformObject node = nodesByPath.get(path);
            if (node != null) {
                children.add(node);
            }
        }
        children.sort(Comparator.comparingInt(PlatformObject::sortOrder)
                .thenComparing(PlatformObject::displayName, String.CASE_INSENSITIVE_ORDER));
        return children;
    }

    /**
     * Returns all nodes whose path starts with {@code prefix} in O(log N + K) time via the sorted path map.
     */
    public List<PlatformObject> findByPathPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return all();
        }
        String upperBound = nextLexicographicPrefix(prefix);
        NavigableMap<String, PlatformObject> slice = upperBound != null
                ? nodesByPath.subMap(prefix, true, upperBound, false)
                : nodesByPath.tailMap(prefix, true);
        return List.copyOf(slice.values());
    }

    /**
     * Returns all nodes matching any of {@code types} via the secondary type index.
     */
    public List<PlatformObject> findByTypes(Set<ObjectType> types) {
        if (types == null || types.isEmpty()) {
            return all();
        }
        List<PlatformObject> result = new ArrayList<>();
        for (ObjectType type : EnumSet.copyOf(types)) {
            Set<String> paths = pathsByType.get(type);
            if (paths == null || paths.isEmpty()) {
                continue;
            }
            for (String path : paths) {
                PlatformObject node = nodesByPath.get(path);
                if (node != null && node.type() == type) {
                    result.add(node);
                }
            }
        }
        return result;
    }

    /**
     * Returns candidate nodes narrowed by static path prefix and optional {@link ObjectType} filter,
     * avoiding full-tree iteration whenever a prefix or type constraint is present.
     */
    public List<PlatformObject> findCandidates(String pathPrefix, Set<ObjectType> types) {
        boolean hasPrefix = pathPrefix != null && !pathPrefix.isBlank();
        boolean hasTypes = types != null && !types.isEmpty();
        if (!hasPrefix && !hasTypes) {
            return all();
        }
        if (hasPrefix) {
            String upperBound = nextLexicographicPrefix(pathPrefix);
            NavigableMap<String, PlatformObject> slice = upperBound != null
                    ? nodesByPath.subMap(pathPrefix, true, upperBound, false)
                    : nodesByPath.tailMap(pathPrefix, true);
            if (!hasTypes) {
                return List.copyOf(slice.values());
            }
            List<PlatformObject> filtered = new ArrayList<>();
            for (PlatformObject node : slice.values()) {
                if (types.contains(node.type())) {
                    filtered.add(node);
                }
            }
            return filtered;
        }
        return findByTypes(types);
    }

    private static String nextLexicographicPrefix(String prefix) {
        for (int i = prefix.length() - 1; i >= 0; i--) {
            char ch = prefix.charAt(i);
            if (ch < Character.MAX_VALUE) {
                return prefix.substring(0, i) + (char) (ch + 1);
            }
        }
        return null;
    }

    public List<PlatformObject> all() {
        return List.copyOf(nodesByPath.values());
    }

    public PlatformObject require(String path) {
        return findByPath(path).orElseThrow(() -> new ObjectNotFoundException(path));
    }

    public String resolveChildPath(String parentPath, String name) {
        if (parentPath == null || parentPath.isBlank()) {
            throw new IllegalArgumentException("parentPath is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("child name is required");
        }
        String trimmed = name.trim();
        if (trimmed.indexOf('.') >= 0) {
            throw new IllegalArgumentException("child name must not contain '.': " + name);
        }
        return parentPath + "." + trimmed;
    }

    public void delete(String path) {
        if ("root".equals(path)) {
            throw new IllegalArgumentException("Cannot delete root object");
        }
        List<String> toRemove = new ArrayList<>();
        if (nodesByPath.containsKey(path)) {
            toRemove.add(path);
        }
        String subtreePrefix = path + ".";
        String subtreeUpper = nextLexicographicPrefix(subtreePrefix);
        NavigableMap<String, PlatformObject> descendants = subtreeUpper != null
                ? nodesByPath.subMap(subtreePrefix, true, subtreeUpper, false)
                : nodesByPath.tailMap(subtreePrefix, true);
        toRemove.addAll(descendants.keySet());
        toRemove.sort((a, b) -> Integer.compare(b.length(), a.length()));
        if (toRemove.isEmpty()) {
            throw new ObjectNotFoundException(path);
        }
        for (String p : toRemove) {
            PlatformObject removed = nodesByPath.remove(p);
            if (removed != null) {
                nodesById.remove(removed.id());
                unindexType(p, removed.type());
                parentPathOf(p).ifPresent(parent -> {
                    Set<String> siblings = childPathsByParent.get(parent);
                    if (siblings != null) {
                        siblings.remove(p);
                        if (siblings.isEmpty()) {
                            childPathsByParent.remove(parent);
                        }
                    }
                });
                childPathsByParent.remove(p);
            }
        }
    }

    private static Optional<String> parentPathOf(String path) {
        if ("root".equals(path)) {
            return Optional.empty();
        }
        int dot = path.lastIndexOf('.');
        if (dot <= 0) {
            return Optional.empty();
        }
        return Optional.of(path.substring(0, dot));
    }
}
