package com.ispf.plugin.blueprint;

/**
 * Model attachment semantics when applying a model to the object tree.
 * <ul>
 *   <li>{@link #RELATIVE} — variables/events are merged into each matching target object</li>
 *   <li>{@link #ABSOLUTE} — model lives as a dedicated singleton object branch</li>
 *   <li>{@link #INSTANCE} — explicit on-demand instantiation under a parent path</li>
 * </ul>
 */
public enum BlueprintType {
    RELATIVE,
    ABSOLUTE,
    INSTANCE
}
