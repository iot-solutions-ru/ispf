package com.ispf.server.platform.settings;

import java.util.List;

public record PlatformRuntimeSettingsSectionView(
        String id,
        String title,
        List<PlatformRuntimeSettingView> settings
) {
}
