package com.ispf.server.application.binding;

import com.ispf.server.platform.PlatformLeaderLockService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ApplicationSqlBindingScheduler {

    private static final String BINDING_LOCK = "application_sql_bindings";

    private final ApplicationSqlBindingService bindingService;
    private final PlatformLeaderLockService leaderLockService;

    public ApplicationSqlBindingScheduler(
            ApplicationSqlBindingService bindingService,
            PlatformLeaderLockService leaderLockService
    ) {
        this.bindingService = bindingService;
        this.leaderLockService = leaderLockService;
    }

    @Scheduled(fixedDelay = 10_000)
    public void refreshScheduledBindings() {
        if (!leaderLockService.tryAcquire(BINDING_LOCK, Duration.ofSeconds(20))) {
            return;
        }
        try {
            bindingService.refreshScheduledBindings();
        } finally {
            leaderLockService.release(BINDING_LOCK);
        }
    }
}
