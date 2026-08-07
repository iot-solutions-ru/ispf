package com.ispf.server.application.bundle;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OperatorLaunchExtrasTest {

    @Test
    void putOperatorLaunchExtraCopiesAdr0054Fields() {
        Map<String, Object> ui = new LinkedHashMap<>();
        ui.put("externalSpaUrl", "https://bridge.example/apps/demo/");
        ui.put("spaNav", List.of(Map.of("to", "/home", "label", "Home")));
        ui.put("uiPack", Map.of("packId", "demo-ui", "version", "1.0.0"));
        ui.put("eventJournalObjectPath", "root.platform.devices.demo.hub");
        ui.put("title", "ignored");

        Map<String, Object> extras = new LinkedHashMap<>();
        ApplicationBundleDeployService.putOperatorLaunchExtra(ui, extras, "externalSpaUrl");
        ApplicationBundleDeployService.putOperatorLaunchExtra(ui, extras, "spaNav");
        ApplicationBundleDeployService.putOperatorLaunchExtra(ui, extras, "uiPack");
        ApplicationBundleDeployService.putOperatorLaunchExtra(ui, extras, "eventJournalObjectPath");
        ApplicationBundleDeployService.putOperatorLaunchExtra(ui, extras, "missing");

        assertThat(extras)
                .containsOnlyKeys("externalSpaUrl", "spaNav", "uiPack", "eventJournalObjectPath")
                .containsEntry("externalSpaUrl", "https://bridge.example/apps/demo/");
        assertThat(extras.get("spaNav")).isInstanceOf(List.class);
    }

    @Test
    void putOperatorLaunchExtraIgnoresNulls() {
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("externalSpaUrl", "keep-me");
        ApplicationBundleDeployService.putOperatorLaunchExtra(Map.of(), extras, "externalSpaUrl");
        assertThat(extras).containsEntry("externalSpaUrl", "keep-me");
    }
}
