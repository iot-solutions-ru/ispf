package com.ispf.server.object.bus;

/**
 * Processing lane for {@link ObjectChangeEventBus} when split-lane mode is enabled.
 */
public enum ObjectChangeLane {
    /** High-volume telemetry history writes ({@code event.telemetry()==true}). */
    TELEMETRY,
    /** Alerts, workflows, correlator, SQL bindings, and other automation reactions. */
    AUTOMATION
}
