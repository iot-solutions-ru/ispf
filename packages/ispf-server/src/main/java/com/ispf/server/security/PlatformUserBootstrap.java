package com.ispf.server.security;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class PlatformUserBootstrap {

    private final PlatformUserService userService;

    public PlatformUserBootstrap(PlatformUserService userService) {
        this.userService = userService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE - 10)
    public void seedUsersAndSyncTree() {
        userService.ensureDefaultUsers();
    }
}
