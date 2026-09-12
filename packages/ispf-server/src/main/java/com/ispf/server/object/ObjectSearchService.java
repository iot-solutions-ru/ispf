package com.ispf.server.object;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Full-tree substring search (path, display name, description). Used by the explorer
 * so operators are not limited to already-expanded lazy folders.
 */
@Service
public class ObjectSearchService {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;
    public static final int MIN_QUERY_CHARS = 2;

    private final ObjectManager objectManager;
    private final ObjectAccessService objectAccessService;
    private final TenantScopeService tenantScopeService;

    public ObjectSearchService(
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        this.objectManager = objectManager;
        this.objectAccessService = objectAccessService;
        this.tenantScopeService = tenantScopeService;
    }

    public Result search(
            String query,
            String type,
            String parentPrefix,
            int limit,
            Authentication authentication
    ) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.length() < MIN_QUERY_CHARS) {
            return Result.empty(needle);
        }
        int cap = Math.max(1, Math.min(limit <= 0 ? DEFAULT_LIMIT : limit, MAX_LIMIT));
        ObjectType typeFilter = parseType(type);
        String prefix = parentPrefix == null ? "" : parentPrefix.trim();

        List<PlatformObject> matches = new ArrayList<>();
        for (PlatformObject node : objectManager.tree().all()) {
            if (matches.size() >= cap) {
                break;
            }
            if (!prefix.isBlank() && !pathUnder(node.path(), prefix)) {
                continue;
            }
            if (typeFilter != null && node.type() != typeFilter) {
                continue;
            }
            if (!isVisible(node.path(), authentication)) {
                continue;
            }
            if (!haystack(node).contains(needle)) {
                continue;
            }
            matches.add(node);
        }
        matches.sort(Comparator.comparing(PlatformObject::path));
        boolean truncated = matches.size() >= cap;

        LinkedHashMap<String, PlatformObject> nodes = new LinkedHashMap<>();
        for (PlatformObject match : matches) {
            addAncestors(match.path(), prefix, authentication, nodes);
            nodes.putIfAbsent(match.path(), match);
        }
        return new Result(needle, matches.size(), truncated, List.copyOf(nodes.values()));
    }

    private void addAncestors(
            String path,
            String prefix,
            Authentication authentication,
            LinkedHashMap<String, PlatformObject> nodes
    ) {
        String parent = parentPath(path);
        List<PlatformObject> chain = new ArrayList<>();
        while (parent != null) {
            if (!prefix.isBlank() && !pathUnder(parent, prefix) && !parent.equals(prefix)) {
                break;
            }
            objectManager.tree().findByPath(parent).ifPresent(node -> {
                if (isVisible(node.path(), authentication)) {
                    chain.add(node);
                }
            });
            parent = parentPath(parent);
        }
        for (int i = chain.size() - 1; i >= 0; i--) {
            PlatformObject ancestor = chain.get(i);
            nodes.putIfAbsent(ancestor.path(), ancestor);
        }
    }

    private boolean isVisible(String path, Authentication authentication) {
        return tenantScopeService.isPathVisible(path, authentication)
                && objectAccessService.canRead(path, authentication);
    }

    private static String haystack(PlatformObject node) {
        String description = node.description() == null ? "" : node.description();
        return (node.path() + " " + node.displayName() + " " + description).toLowerCase(Locale.ROOT);
    }

    private static ObjectType parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return ObjectType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static boolean pathUnder(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + ".");
    }

    static String parentPath(String path) {
        if (path == null || path.isBlank() || "root".equals(path)) {
            return null;
        }
        int dot = path.lastIndexOf('.');
        return dot <= 0 ? null : path.substring(0, dot);
    }

    public record Result(String query, int matchCount, boolean truncated, List<PlatformObject> nodes) {
        static Result empty(String query) {
            return new Result(query, 0, false, List.of());
        }
    }
}
