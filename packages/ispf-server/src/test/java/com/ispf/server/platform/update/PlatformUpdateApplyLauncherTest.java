package com.ispf.server.platform.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformUpdateApplyLauncherTest {

    @TempDir
    Path tempDir;

    @Test
    void buildCommandUsesSystemdRunWhenAvailable() {
        Path script = tempDir.resolve("apply-platform-update.sh");
        Path staging = tempDir.resolve("0.7.4");

        List<String> command = PlatformUpdateApplyLauncher.buildCommand(
                script,
                staging,
                "/usr/bin/systemd-run"
        );

        assertThat(command).containsExactly(
                "/usr/bin/systemd-run",
                "--collect",
                "--unit=ispf-platform-update-0.7.4",
                "--description=ISPF platform update 0.7.4",
                "--",
                "bash",
                script.toAbsolutePath().normalize().toString(),
                staging.toAbsolutePath().normalize().toString()
        );
    }

    @Test
    void buildCommandFallsBackToSetsidWhenSystemdRunMissing() {
        Path script = tempDir.resolve("apply.sh");
        Path staging = tempDir.resolve("release");

        List<String> command = PlatformUpdateApplyLauncher.buildCommand(script, staging, null);

        assertThat(command).hasSize(3);
        assertThat(command.get(0)).isEqualTo("bash");
        assertThat(command.get(1)).isEqualTo("-c");
        assertThat(command.get(2)).contains("setsid bash -c");
        assertThat(command.get(2)).contains(script.toAbsolutePath().normalize().toString());
        assertThat(command.get(2)).contains(staging.resolve("apply.log").toAbsolutePath().normalize().toString());
    }

    @Test
    void sanitizeUnitSuffixReplacesInvalidCharacters() {
        assertThat(PlatformUpdateApplyLauncher.sanitizeUnitSuffix(Path.of("/tmp/staging/v0.7.4+build")))
                .isEqualTo("v0.7.4-build");
    }
}
