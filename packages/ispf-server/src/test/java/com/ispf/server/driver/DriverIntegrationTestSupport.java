package com.ispf.server.driver;

import com.ispf.core.object.ObjectType;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.blueprint.BlueprintApplicationService;

/**
 * Creates DEVICE objects without a pre-provisioned driver so integration tests can configure any driverId.
 */
final class DriverIntegrationTestSupport {

    private DriverIntegrationTestSupport() {
    }

    static String createDevice(
            ObjectManager objectManager,
            BlueprintApplicationService blueprintApplicationService,
            DriverRuntimeService driverRuntimeService,
            String name
    ) {
        String parent = "root.platform.devices";
        String path = parent + "." + name;
        objectManager.create(parent, name, ObjectType.DEVICE, name, null, null);
        blueprintApplicationService.applyMixinBlueprintsWithRules(path);
        driverRuntimeService.stopIfRunning(path);
        return path;
    }

    static void deleteDevice(ObjectManager objectManager, DriverRuntimeService driverRuntimeService, String path) {
        try {
            driverRuntimeService.stopIfRunning(path);
        } catch (Exception ignored) {
            // best effort
        }
        try {
            objectManager.delete(path);
        } catch (Exception ignored) {
            // best effort
        }
    }
}
