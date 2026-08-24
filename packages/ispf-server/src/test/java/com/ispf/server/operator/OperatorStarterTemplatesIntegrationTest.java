package com.ispf.server.operator;

import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ispf.bootstrap.fixtures-enabled=false",
        "ispf.bootstrap.mes-catalog-enabled=false"
})
class OperatorStarterTemplatesIntegrationTest {

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private OperatorStarterTemplatesService starterTemplatesService;

    @Test
    void applicationReady_ensuresPlatformOperatorStartersWithoutFixtures() {
        assertThat(objectManager.tree().findByPath("root.platform.dashboards.alarm-console"))
                .isPresent();
        assertThat(objectManager.tree().findByPath("root.platform.dashboards.work-queue"))
                .isPresent();
        assertThat(objectManager.tree().findByPath("root.platform.dashboards.hmi-wall"))
                .isPresent();
    }

    @Test
    void installStarters_isIdempotentWhenDashboardsExist() throws Exception {
        var first = starterTemplatesService.installStarters(false);
        var second = starterTemplatesService.installStarters(false);

        assertThat(first.get("installed")).isEqualTo(second.get("installed"));
        assertThat(objectManager.tree().findByPath("root.platform.dashboards.alarm-console"))
                .isPresent();
    }
}
