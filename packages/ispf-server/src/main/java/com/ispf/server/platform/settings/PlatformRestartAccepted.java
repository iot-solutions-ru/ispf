package com.ispf.server.platform.settings;

public record PlatformRestartAccepted(
        boolean accepted,
        long delayMs,
        String mode,
        String message
) {
}
