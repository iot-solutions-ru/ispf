package com.ispf.plugin.blueprint;

/**
 * SQL binding template contributed by a blueprint and materialized per target object.
 */
public record BlueprintSqlBindingTemplate(
        String variable,
        String query,
        String dataSourcePath,
        Long refreshIntervalMs,
        String valueField
) {
}
