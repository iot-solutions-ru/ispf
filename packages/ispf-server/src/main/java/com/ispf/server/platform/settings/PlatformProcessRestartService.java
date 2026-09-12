package com.ispf.server.platform.settings;

import com.ispf.server.config.PlatformRestartProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class PlatformProcessRestartService {

    private static final Logger log = LoggerFactory.getLogger(PlatformProcessRestartService.class);

    private final PlatformRestartProperties properties;
    private final ConfigurableApplicationContext applicationContext;
    private final AtomicBoolean scheduled = new AtomicBoolean(false);

    public PlatformProcessRestartService(
            PlatformRestartProperties properties,
            ConfigurableApplicationContext applicationContext
    ) {
        this.properties = properties;
        this.applicationContext = applicationContext;
    }

    public PlatformRestartAccepted scheduleRestart() {
        if (!properties.isEnabled()) {
            return new PlatformRestartAccepted(
                    false,
                    0,
                    "disabled",
                    "Process restart is disabled in this profile"
            );
        }
        long delayMs = Math.max(500L, properties.getDelayMs());
        String mode = resolveMode();
        if (!scheduled.compareAndSet(false, true)) {
            return new PlatformRestartAccepted(true, delayMs, mode, "Restart already scheduled");
        }
        Thread worker = new Thread(() -> {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                scheduled.set(false);
                return;
            }
            performRestart(mode);
        }, "ispf-platform-restart");
        worker.setDaemon(false);
        worker.start();
        return new PlatformRestartAccepted(
                true,
                delayMs,
                mode,
                "Restart scheduled. The server will stop in " + delayMs + " ms."
        );
    }

    void performRestart(String mode) {
        if ("systemd".equals(mode)) {
            try {
                List<String> command = PlatformProcessRestartLauncher.buildSystemdCommand(
                        properties.getUnit(),
                        PlatformProcessRestartLauncher.resolveSystemdRun()
                );
                log.info("Scheduling systemd restart via {}", command);
                new ProcessBuilder(command)
                        .redirectInput(ProcessBuilder.Redirect.DISCARD)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
                return;
            } catch (IOException ex) {
                log.warn("systemd restart failed, falling back to process exit", ex);
            }
        }
        log.info("Exiting JVM for process restart (mode={})", mode);
        int code = SpringApplication.exit(applicationContext, () -> 0);
        System.exit(code);
    }

    String resolveMode() {
        if (PlatformProcessRestartLauncher.systemdUnitPresent(properties.getUnit())) {
            return "systemd";
        }
        return "exit";
    }
}
