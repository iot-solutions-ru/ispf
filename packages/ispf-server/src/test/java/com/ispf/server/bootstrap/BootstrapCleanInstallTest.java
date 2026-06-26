package com.ispf.server.bootstrap;

import com.ispf.server.object.ObjectManager;
import com.ispf.server.operator.OperatorAppUiStore;
import com.ispf.server.security.PlatformUserService;
import com.ispf.plugin.model.ModelRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "ispf.bootstrap.fixtures-enabled=false")
class BootstrapCleanInstallTest {

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private ModelRegistry modelRegistry;

    @Autowired
    private PlatformUserService userService;

    @Autowired
    private OperatorAppUiStore operatorAppUiStore;

    @Test
    void doesNotSeedFixtureModels() {
        for (String name : FixtureModelBootstrap.FIXTURE_MODEL_NAMES) {
            assertThat(modelRegistry.findByName(name)).isEmpty();
            assertThat(objectManager.tree().findByPath("root.platform.relative-models." + name)).isEmpty();
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
