package com.ispf.server.bootstrap;

import com.ispf.server.application.reference.mes.MesBlueprintBootstrap;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.operator.OperatorAppUiStore;
import com.ispf.server.security.PlatformUserService;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "ispf.bootstrap.fixtures-enabled=false")
class BootstrapCleanInstallTest {

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private BlueprintRegistry BlueprintRegistry;

    @Autowired
    private PlatformUserService userService;

    @Autowired
    private OperatorAppUiStore operatorAppUiStore;

    @Test
    void registersVirtualLabModelsWithoutFixtures() {
        assertThat(BlueprintRegistry.findByName(LabBlueprintBootstrap.VIRTUAL_LAB_MODEL)).isPresent();
        assertThat(BlueprintRegistry.findByName(LabBlueprintBootstrap.VIRTUAL_UNIFIED_MODEL)).isPresent();
        assertThat(BlueprintRegistry.findByName(PlatformReferenceBlueprintBootstrap.SNMP_AGENT_MODEL)).isPresent();
        assertThat(BlueprintRegistry.findByName(PlatformReferenceBlueprintBootstrap.MQTT_GATEWAY_SENSOR_MODEL)).isPresent();
    }

    @Test
    void doesNotRegisterMesInstanceTypesOnCleanBasePlatform() {
        assertThat(BlueprintRegistry.findByName(MesBlueprintBootstrap.BATCH_MODEL)).isEmpty();
        assertThat(BlueprintRegistry.findByName(MesBlueprintBootstrap.WORK_ORDER_MODEL)).isEmpty();
        assertThat(objectManager.tree().findByPath("root.platform.mes")).isEmpty();
        assertThat(objectManager.tree().findByPath("root.platform.instance-types.batch-v1")).isEmpty();
        assertThat(objectManager.tree().findByPath("root.platform.instance-types.work-order-v1")).isEmpty();
    }

    @Test
    void doesNotSeedDemoObjects() {
        assertThat(objectManager.tree().findByPath("root.platform.devices.demo-sensor-01")).isEmpty();
        assertThat(objectManager.tree().findByPath("root.platform.devices.snmp-localhost")).isEmpty();
        assertThat(objectManager.tree().findByPath("root.platform.dashboards.demo-sensor")).isEmpty();
        assertThat(objectManager.tree().findByPath("root.platform.dashboards.snmp-host-monitoring")).isEmpty();
        assertThat(objectManager.tree().findByPath("root.platform.workflows.demo-alarm-handler")).isEmpty();
    }

    @Test
    void doesNotSeedFixtureModels() {
        for (String name : DemoFixtureBootstrap.DEMO_MODEL_NAMES) {
            assertThat(BlueprintRegistry.findByName(name)).isEmpty();
            assertThat(objectManager.tree().findByPath("root.platform.mixin-blueprints." + name)).isEmpty();
            assertThat(objectManager.tree().findByPath("root.platform.instance-types." + name)).isEmpty();
        }
    }

    @Test
    void doesNotSeedLabUsersOrPlatformHmi() {
        assertThat(userService.listUsers().stream().map(u -> u.get("username")))
                .doesNotContain(LabSecurityBootstrap.LAB_USER_A, LabSecurityBootstrap.LAB_USER_B);
        assertThat(operatorAppUiStore.findByAppId("platform")).isEmpty();
        assertThat(objectManager.tree().findByPath("root.platform.operator-apps.platform")).isEmpty();
    }
}
