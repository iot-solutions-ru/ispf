package com.ispf.server.platform.settings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Detached restart so {@code systemctl restart ispf-server} is not killed with this JVM.
 */
final class PlatformProcessRestartLauncher {

    private PlatformProcessRestartLauncher() {
    }

    static List<String> buildSystemdCommand(String unit, String systemdRunOverride) {
        String service = unit.endsWith(".service") ? unit : unit + ".service";
        if (systemdRunOverride != null) {
            return List.of(
                    systemdRunOverride,
                    "--no-block",
                    "--collect",
                    "--unit=ispf-platform-restart-" + sanitize(service),
                    "--description=ISPF process restart " + service,
                    "--",
                    "systemctl",
                    "restart",
                    service
            );
        }
        String inner = "sleep 2; exec systemctl restart " + shellQuote(service);
        String command = "setsid bash -c " + shellQuote(inner) + " </dev/null >/dev/null 2>&1 &";
        return List.of("bash", "-c", command);
    }

    static String resolveSystemdRun() {
        for (String candidate : List.of("/usr/bin/systemd-run", "/bin/systemd-run")) {
            if (Files.isExecutable(Path.of(candidate))) {
                return candidate;
            }
        }
        return null;
    }

    static boolean systemdUnitPresent(String unit) {
        String service = unit.endsWith(".service") ? unit : unit + ".service";
        for (String dir : List.of("/etc/systemd/system", "/lib/systemd/system", "/usr/lib/systemd/system")) {
            if (Files.isRegularFile(Path.of(dir, service))) {
                return true;
            }
        }
        return false;
    }

    static String sanitize(String value) {
        String name = value.replaceAll("[^a-zA-Z0-9._-]", "-");
        return name.isBlank() ? "run" : name;
    }

    static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
