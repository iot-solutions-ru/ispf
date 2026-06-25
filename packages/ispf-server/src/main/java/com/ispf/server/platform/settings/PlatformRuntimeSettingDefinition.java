package com.ispf.server.platform.settings;

record PlatformRuntimeSettingDefinition(
        String id,
        String sectionId,
        String envVar,
        String propertyKey,
        PlatformRuntimeSettingType type,
        String defaultValue,
        boolean sensitive,
        boolean hotReloadable
) {
}
