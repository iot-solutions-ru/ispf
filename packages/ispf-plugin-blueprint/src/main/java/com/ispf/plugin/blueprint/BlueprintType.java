package com.ispf.plugin.blueprint;

/**
 * Model attachment semantics when applying a model to the object tree.
 * <ul>
 *   <li>{@link #MIXIN} — variables/events are merged into each matching target object</li>
 *   <li>{@link #SINGLETON} — unique live node under {@code root.platform.singleton-blueprints.*} with application logic</li>
 *   <li>{@link #INSTANCE} — explicit on-demand instantiation under a parent path</li>
 * </ul>
 */
public enum BlueprintType {
    MIXIN,
    SINGLETON,
    INSTANCE
}
