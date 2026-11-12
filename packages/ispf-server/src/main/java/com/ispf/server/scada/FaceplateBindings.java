package com.ispf.server.scada;

/**
 * Resolves faceplate / mimic binding paths relative to a root object.
 */
public final class FaceplateBindings {

    private FaceplateBindings() {
    }

    /**
     * Joins a relative segment under {@code rootObjectPath}, or accepts an absolute path only when
     * it stays under that root.
     */
    public static String resolve(String rootObjectPath, String relativePath) {
        if (rootObjectPath == null || rootObjectPath.isBlank()) {
            throw new IllegalArgumentException("rootObjectPath is required");
        }
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("relativePath is required");
        }
        String root = rootObjectPath.trim();
        String candidate = relativePath.trim();
        if (containsEscapeSegment(candidate)) {
            throw new IllegalArgumentException("binding path escapes root: " + candidate);
        }
        if (candidate.startsWith("root.")) {
            if (!isUnderRoot(root, candidate)) {
                throw new IllegalArgumentException("binding path escapes root: " + candidate);
            }
            return candidate;
        }
        String suffix = candidate.startsWith(".") ? candidate.substring(1) : candidate;
        if (suffix.isBlank()) {
            return root;
        }
        if (containsEscapeSegment(suffix)) {
            throw new IllegalArgumentException("binding path escapes root: " + candidate);
        }
        String resolved = root + "." + suffix;
        if (!isUnderRoot(root, resolved)) {
            throw new IllegalArgumentException("binding path escapes root: " + candidate);
        }
        return resolved;
    }

    static boolean isUnderRoot(String root, String path) {
        return path.equals(root) || path.startsWith(root + ".");
    }

    private static boolean containsEscapeSegment(String path) {
        if (path.startsWith("..")) {
            return true;
        }
        for (String segment : path.split("\\.", -1)) {
            if ("..".equals(segment) || segment.isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
