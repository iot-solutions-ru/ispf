package com.ispf.server.function.java;

import com.ispf.core.object.FunctionDescriptor;
import com.ispf.server.object.ObjectManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class JavaFunctionBootstrap {

    private static final Logger log = LoggerFactory.getLogger(JavaFunctionBootstrap.class);

    private final ObjectManager objectManager;
    private final JavaFunctionRuntimeService runtimeService;
    private final Environment environment;

    public JavaFunctionBootstrap(
            ObjectManager objectManager,
            JavaFunctionRuntimeService runtimeService,
            Environment environment
    ) {
        this.objectManager = objectManager;
        this.runtimeService = runtimeService;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE - 5)
    public void warmUpCompiledFunctions() {
        if (!runtimeService.isEnabled()) {
            log.info("Java function warm-up skipped (ispf.function.java.enabled=false)");
            return;
        }
        // Prod defaults to disabled; WARN when operators explicitly re-enable in-process execution.
        if (environment.matchesProfiles("prod")) {
            log.warn(
                    "In-process Java functions are ENABLED on a prod profile "
                            + "(ispf.function.java.enabled=true): admin-authored code executes inside "
                            + "the server JVM — the regex denylist is not a process sandbox (ADR-0045)."
            );
        }
        for (var node : objectManager.tree().all()) {
            for (FunctionDescriptor function : node.functions().values()) {
                if (!function.hasJavaBody()) {
                    continue;
                }
                try {
                    runtimeService.compileAndRegister(node.path(), function);
                } catch (RuntimeException ex) {
                    log.warn(
                            "Failed to compile Java function {} on {} during startup: {}",
                            function.name(),
                            node.path(),
                            ex.getMessage()
                    );
                }
            }
        }
    }
}
