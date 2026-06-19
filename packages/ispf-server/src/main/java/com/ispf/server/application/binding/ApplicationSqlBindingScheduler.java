package com.ispf.server.application.binding;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ApplicationSqlBindingScheduler {

    private final ApplicationSqlBindingService bindingService;

    public ApplicationSqlBindingScheduler(ApplicationSqlBindingService bindingService) {
        this.bindingService = bindingService;
    }

    @Scheduled(fixedDelay = 10_000)
    public void refreshScheduledBindings() {
        bindingService.refreshScheduledBindings();
    }
}
