package com.ispf.server.platform.settings;

public record PlatformRuntimeSettingView(
        String id,
        String envVar,
        String propertyKey,
        String type,
        String value,
        String defaultValue,
        String source,
        boolean sensitive,
        boolean editable,
        boolean hotReloadable,
        boolean restartRequired
) {
}
