package com.ispf.server.platform.settings;

import java.util.Map;

public record PlatformRuntimeSettingsPatchRequest(
        Map<String, String> values
) {
}
