package com.ispf.server.platform.settings;

import java.util.List;

public record PlatformRuntimeSettingsResponse(
        String settingsFile,
        List<PlatformRuntimeSettingsSectionView> sections
) {
}
