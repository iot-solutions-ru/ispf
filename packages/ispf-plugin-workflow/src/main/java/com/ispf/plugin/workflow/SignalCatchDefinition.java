package com.ispf.plugin.workflow;

import java.util.Map;

public record SignalCatchDefinition(
        String id,
        String name,
        String signalName,
        Map<String, String> parameters
) {
}
