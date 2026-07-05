package com.ispf.server.bootstrap;

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
    }

    @Test
    void doesNotSeedFixtureModels() {
        for (String name : FixtureBlueprintBootstrap.FIXTURE_MODEL_NAMES) {
            assertThat(BlueprintRegistry.findByName(name)).isEmpty();
            assertThat(objectManager.tree().findByPath("root.platform.relative-blueprints." + name)).isEmpty();
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
