package com.ispf.server.security;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class PlatformUserBootstrap {

    private final PlatformRoleService roleService;
    private final PlatformUserService userService;

    public PlatformUserBootstrap(PlatformRoleService roleService, PlatformUserService userService) {
        this.roleService = roleService;
        this.userService = userService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 5)
    public void seedUsersAndSyncTree() {
        roleService.ensureDefaultRoles();
        userService.ensureDefaultUsers();
    }
}
