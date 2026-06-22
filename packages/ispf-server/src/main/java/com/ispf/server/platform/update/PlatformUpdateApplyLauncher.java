package com.ispf.server.platform.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * Starts the platform apply script detached from the ISPF JVM/service cgroup so
 * {@code systemctl stop ispf-server} inside the script does not kill the updater.
 */
final class PlatformUpdateApplyLauncher {

    private PlatformUpdateApplyLauncher() {
    }

    static void launch(Path applyScript, Path stagingRoot) throws IOException {
        Files.createDirectories(stagingRoot);
        Files.writeString(
                stagingRoot.resolve("apply.log"),
                "",
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );

        ProcessBuilder builder = new ProcessBuilder(buildCommand(applyScript, stagingRoot));
        builder.redirectInput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.start();
    }

    static List<String> buildCommand(Path applyScript, Path stagingRoot) {
        return buildCommand(applyScript, stagingRoot, resolveSystemdRun());
    }

    static List<String> buildCommand(Path applyScript, Path stagingRoot, String systemdRunOverride) {
        String script = applyScript.toAbsolutePath().normalize().toString();
        String staging = stagingRoot.toAbsolutePath().normalize().toString();

        if (systemdRunOverride != null) {
            return List.of(
                    systemdRunOverride,
                    "--collect",
                    "--unit=ispf-platform-update-" + sanitizeUnitSuffix(stagingRoot),
                    "--description=ISPF platform update " + stagingRoot.getFileName(),
                    "--",
                    "bash",
                    script,
                    staging
            );
        }

        Path logFile = stagingRoot.resolve("apply.log").toAbsolutePath().normalize();
        String inner = "exec bash "
                + shellQuote(script)
                + " "
                + shellQuote(staging)
                + " >> "
                + shellQuote(logFile.toString())
                + " 2>&1";
        String command = "setsid bash -c " + shellQuote(inner) + " </dev/null >/dev/null 2>&1 &";
        return List.of("bash", "-c", command);
    }

    private static String resolveSystemdRun() {
        for (String candidate : List.of("/usr/bin/systemd-run", "/bin/systemd-run")) {
            if (Files.isExecutable(Path.of(candidate))) {
                return candidate;
            }
        }
        return null;
    }

    static String sanitizeUnitSuffix(Path stagingRoot) {
        String name = stagingRoot.getFileName().toString().replaceAll("[^a-zA-Z0-9._-]", "-");
        return name.isBlank() ? "run" : name;
    }

    static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
