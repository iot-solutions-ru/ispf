package com.ispf.server.api;

import com.ispf.server.platform.settings.PlatformProcessRestartService;
import com.ispf.server.platform.settings.PlatformRestartAccepted;
import com.ispf.server.platform.settings.PlatformRuntimeSettingsPatchRequest;
import com.ispf.server.platform.settings.PlatformRuntimeSettingsPatchResult;
import com.ispf.server.platform.settings.PlatformRuntimeSettingsResponse;
import com.ispf.server.platform.settings.PlatformRuntimeSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformRuntimeSettingsController {

    private final PlatformRuntimeSettingsService settingsService;
    private final PlatformProcessRestartService restartService;

    public PlatformRuntimeSettingsController(
            PlatformRuntimeSettingsService settingsService,
            PlatformProcessRestartService restartService
    ) {
        this.settingsService = settingsService;
        this.restartService = restartService;
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

    @PostMapping("/runtime-settings/restart")
    public ResponseEntity<PlatformRestartAccepted> restartRuntime() {
        PlatformRestartAccepted result = restartService.scheduleRestart();
        if (!result.accepted()) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.accepted().body(result);
    }
}
