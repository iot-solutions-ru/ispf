package com.ispf.server.driver;

import com.ispf.core.object.ObjectType;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.blueprint.BlueprintApplicationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
@ActiveProfiles("test")
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class DriverDeleteStopsRuntimeTest {

    private static final String FOLDER = "root.platform.devices.delete-driver-folder";
    private static final String DEVICE = FOLDER + ".dev-01";

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private BlueprintApplicationService blueprintApplicationService;

    @Autowired
    private DeviceProvisioningService deviceProvisioningService;

    @Autowired
    private DriverRuntimeService driverRuntimeService;

    @AfterEach
    void cleanup() {
        driverRuntimeService.stopIfRunning(DEVICE);
        if (objectManager.tree().findByPath(FOLDER).isPresent()) {
            objectManager.delete(FOLDER);
        }
    }

    @Test
    void deletingDeviceStopsTheInMemoryDriver() {
        ensureFolder();
        String path = createAndStartDevice("dev-01");

        objectManager.delete(path);

        assertThat(driverRuntimeService.isActiveLocally(path)).isFalse();
        assertThat(objectManager.tree().findByPath(path)).isEmpty();
    }

    @Test
    void deletingParentStopsChildDrivers() {
        ensureFolder();
        String path = createAndStartDevice("dev-01");

        objectManager.delete(FOLDER);

        assertThat(driverRuntimeService.isActiveLocally(path)).isFalse();
        assertThat(objectManager.tree().findByPath(FOLDER)).isEmpty();
    }

    @Test
    void stopWorksAfterTreeNodeIsAlreadyGone() {
        ensureFolder();
        String path = createAndStartDevice("dev-01");
        // Simulate the old leak: remove the tree without going through TreeCrudService.stopDriversUnder.
        objectManager.removePathFromMemoryIfPresent(path);
        assertThat(driverRuntimeService.isActiveLocally(path)).isTrue();

        assertThatCode(() -> driverRuntimeService.stop(path)).doesNotThrowAnyException();
        assertThat(driverRuntimeService.isActiveLocally(path)).isFalse();
        assertThat(driverRuntimeService.status(path)).isEmpty();
    }

    private void ensureFolder() {
        if (objectManager.tree().findByPath(FOLDER).isEmpty()) {
            objectManager.create(
                    "root.platform.devices",
                    "delete-driver-folder",
                    ObjectType.VISUAL_GROUP,
                    "delete-driver-folder",
                    "",
                    null
            );
        }
    }

    private String createAndStartDevice(String name) {
        String path = FOLDER + "." + name;
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(FOLDER, name, ObjectType.DEVICE, name, "", null);
            blueprintApplicationService.applyMixinBlueprintsWithRules(path);
        }
        deviceProvisioningService.provisionDriver(path, "virtual", 1000, false);
        driverRuntimeService.configure(
                path,
                DriverBinding.of("virtual", 1000, Map.of(), Map.of())
        );
        driverRuntimeService.start(path);
        assertThat(driverRuntimeService.isActiveLocally(path)).isTrue();
        return path;
    }
}
