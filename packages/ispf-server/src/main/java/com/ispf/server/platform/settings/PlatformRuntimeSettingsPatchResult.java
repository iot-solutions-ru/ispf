package com.ispf.server.platform.settings;

import java.util.List;

public record PlatformRuntimeSettingsPatchResult(
        boolean restartRequired,
        List<String> appliedLive,
        List<String> skippedEnvLocked,
        List<String> errors
) {
}
