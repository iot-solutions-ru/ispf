package com.ispf.server.application.reference.minitec;

import com.ispf.server.config.BootstrapProperties;
import com.ispf.server.config.IspfRoles;
import com.ispf.server.platform.ClusterPlatformBootstrapService;
import com.ispf.server.security.PlatformUserService;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.security.acl.ObjectAclStore;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Demo RBAC for mini-TEC zone operators (gas vs electrical areas).
 */
@Component
public class MiniTecSecurityBootstrap {

    public static final String OPERATOR_GAS = "operator-gas";
    public static final String OPERATOR_ELECTRICAL = "operator-electrical";
    public static final String OPERATOR_ENGINEER = "operator-engineer";

    private final PlatformUserService userService;
    private final ObjectAccessService objectAccessService;
    private final ObjectAclStore aclStore;
    private final BootstrapProperties bootstrapProperties;
    private final ClusterPlatformBootstrapService clusterBootstrapService;

    public MiniTecSecurityBootstrap(
            PlatformUserService userService,
            ObjectAccessService objectAccessService,
            ObjectAclStore aclStore,
            BootstrapProperties bootstrapProperties,
            ClusterPlatformBootstrapService clusterBootstrapService
    ) {
        this.userService = userService;
        this.objectAccessService = objectAccessService;
        this.aclStore = aclStore;
        this.bootstrapProperties = bootstrapProperties;
        this.clusterBootstrapService = clusterBootstrapService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE - 4)
    @Transactional
    public void onReady() {
        if (!bootstrapProperties.isFixturesEnabled() || !clusterBootstrapService.shouldRunFixtureBootstrap()) {
            return;
        }
        userService.ensureUser(OPERATOR_GAS, "Operator Gas Zone", "operator-gas", List.of(IspfRoles.OPERATOR));
        userService.ensureUser(OPERATOR_ELECTRICAL, "Operator Electrical", "operator-electrical", List.of(IspfRoles.OPERATOR));
        userService.ensureUser(OPERATOR_ENGINEER, "Station Engineer", "operator-engineer", List.of(IspfRoles.OPERATOR));

        ensureZoneAcl(MiniTecPaths.GRPB, OPERATOR_GAS);
        ensureZoneAcl(MiniTecPaths.RUMB, OPERATOR_ELECTRICAL);
        ensureZoneAcl(MiniTecPaths.LOAD_MODULE, OPERATOR_ELECTRICAL);
    }

    private void ensureZoneAcl(String devicePath, String owner) {
        if (!aclStore.listByPath(devicePath).isEmpty()) {
            return;
        }
        objectAccessService.replaceEntries(devicePath, List.of(
                new ObjectAclStore.ObjectAclEntryDraft("USER", owner, "OWNER")
        ));
    }
}
