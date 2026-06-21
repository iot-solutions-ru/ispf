package com.ispf.server.bootstrap;

import com.ispf.server.config.IspfRoles;
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
 * Phase 15 lab training: two operator users and cross-device ACL (Task 1).
 */
@Component
public class LabSecurityBootstrap {

    public static final String LAB_USER_A = "lab-user-a";
    public static final String LAB_USER_B = "lab-user-b";
    public static final String LAB_DEVICE_B = LabTrainingBundleLayouts.LAB_DEVICE_B;

    private final PlatformUserService userService;
    private final ObjectAccessService objectAccessService;
    private final ObjectAclStore aclStore;

    public LabSecurityBootstrap(
            PlatformUserService userService,
            ObjectAccessService objectAccessService,
            ObjectAclStore aclStore
    ) {
        this.userService = userService;
        this.objectAccessService = objectAccessService;
        this.aclStore = aclStore;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE - 5)
    @Transactional
    public void onReady() {
        userService.ensureUser(LAB_USER_A, "Lab User A", "lab-user-a", List.of(IspfRoles.OPERATOR));
        userService.ensureUser(LAB_USER_B, "Lab User B", "lab-user-b", List.of(IspfRoles.OPERATOR));
        ensureLabDeviceAcl(LabTrainingBundleLayouts.LAB_DEVICE_A, LAB_USER_A, LAB_USER_B);
        ensureLabDeviceAcl(LAB_DEVICE_B, LAB_USER_B, LAB_USER_A);
    }

    private void ensureLabDeviceAcl(String devicePath, String ownerUsername, String sharedUsername) {
        if (!aclStore.listByPath(devicePath).isEmpty()) {
            return;
        }
        objectAccessService.replaceEntries(devicePath, List.of(
                new ObjectAclStore.ObjectAclEntryDraft("USER", ownerUsername, "OWNER"),
                new ObjectAclStore.ObjectAclEntryDraft("USER", sharedUsername, "EDITOR")
        ));
    }
}
