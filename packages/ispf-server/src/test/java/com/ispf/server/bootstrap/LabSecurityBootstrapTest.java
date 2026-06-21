package com.ispf.server.bootstrap;

import com.ispf.server.security.PlatformUserService;
import com.ispf.server.security.acl.ObjectAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class LabSecurityBootstrapTest {

    @Autowired
    private PlatformUserService userService;

    @Autowired
    private ObjectAccessService objectAccessService;

    @Test
    void seedsLabUsersAndCrossDeviceAcl() {
        assertTrue(userService.listUsers().stream()
                .anyMatch(user -> LabSecurityBootstrap.LAB_USER_A.equals(user.get("username"))));
        assertTrue(userService.listUsers().stream()
                .anyMatch(user -> LabSecurityBootstrap.LAB_USER_B.equals(user.get("username"))));

        var deviceBEntries = objectAccessService.listEntries(LabSecurityBootstrap.LAB_DEVICE_B);
        assertTrue(deviceBEntries.stream().anyMatch(entry ->
                "USER".equalsIgnoreCase(entry.principalType())
                        && LabSecurityBootstrap.LAB_USER_A.equals(entry.principalId())
                        && "EDITOR".equalsIgnoreCase(entry.permission())));
        assertTrue(deviceBEntries.stream().anyMatch(entry ->
                "USER".equalsIgnoreCase(entry.principalType())
                        && LabSecurityBootstrap.LAB_USER_B.equals(entry.principalId())
                        && "OWNER".equalsIgnoreCase(entry.permission())));

        var deviceAEntries = objectAccessService.listEntries(LabTrainingBundleLayouts.LAB_DEVICE_A);
        assertFalse(deviceAEntries.isEmpty());
    }
}
