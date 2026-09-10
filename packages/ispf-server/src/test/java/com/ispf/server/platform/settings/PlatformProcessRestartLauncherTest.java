package com.ispf.server.platform.settings;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformProcessRestartLauncherTest {

    @Test
    void buildSystemdCommandUsesSystemdRunWhenAvailable() {
        List<String> command = PlatformProcessRestartLauncher.buildSystemdCommand(
                "ispf-server",
                "/usr/bin/systemd-run"
        );
        assertThat(command).containsExactly(
                "/usr/bin/systemd-run",
                "--no-block",
                "--collect",
                "--unit=ispf-platform-restart-ispf-server.service",
                "--description=ISPF process restart ispf-server.service",
                "--",
                "systemctl",
                "restart",
                "ispf-server.service"
        );
    }

    @Test
    void buildSystemdCommandFallsBackToSetsid() {
        List<String> command = PlatformProcessRestartLauncher.buildSystemdCommand("ispf-server", null);
        assertThat(command).containsExactly("bash", "-c", command.get(2));
        assertThat(command.get(2)).contains("systemctl restart");
        assertThat(command.get(2)).contains("ispf-server.service");
    }
}
