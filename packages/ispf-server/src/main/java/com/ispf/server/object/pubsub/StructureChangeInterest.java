package com.ispf.server.object.pubsub;

/**
 * Subscribers for structural {@link com.ispf.server.object.ObjectChangeType} events (ADR-0024).
 */
public record StructureChangeInterest(
        boolean uiRefresh,
        boolean platformMaintenance,
        boolean federationExport,
        boolean natsBridge
) {
    public static final StructureChangeInterest NONE = new StructureChangeInterest(false, false, false, false);

    public boolean hasAny() {
        return uiRefresh || platformMaintenance || federationExport || natsBridge;
    }
}
