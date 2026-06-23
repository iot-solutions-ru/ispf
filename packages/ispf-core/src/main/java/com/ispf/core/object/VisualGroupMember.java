package com.ispf.core.object;

/**
 * Reference to an object shown inside a {@link ObjectType#VISUAL_GROUP}.
 */
public record VisualGroupMember(String path, int sortOrder) {
}
