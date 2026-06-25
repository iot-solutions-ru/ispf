package com.ispf.server.api;

import com.ispf.server.platform.settings.PlatformRuntimeSettingsPatchRequest;
import com.ispf.server.platform.settings.PlatformRuntimeSettingsPatchResult;
import com.ispf.server.platform.settings.PlatformRuntimeSettingsResponse;
import com.ispf.server.platform.settings.PlatformRuntimeSettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformRuntimeSettingsController {

    private final PlatformRuntimeSettingsService settingsService;

    public PlatformRuntimeSettingsController(PlatformRuntimeSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/runtime-settings")
    public PlatformRuntimeSettingsResponse runtimeSettings() {
        return settingsService.snapshot();
    }

    @PatchMapping("/runtime-settings")
    public PlatformRuntimeSettingsPatchResult patchRuntimeSettings(
            @RequestBody PlatformRuntimeSettingsPatchRequest request
    ) {
        return settingsService.patch(request);
    }
}
