package com.ispf.server.migration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationObjectServiceScopeTest {

    private static MigrationObjectService.MigrationDefinition migration(
            String scriptId,
            String version,
            String dataSourcePath
    ) {
        return new MigrationObjectService.MigrationDefinition(
                "root.platform.migrations." + scriptId,
                scriptId,
                version,
                dataSourcePath,
                "SELECT 1"
        );
    }

    @Test
    void matchesPendingFilterByVersionAndDataSource() {
        var mqtt = migration("sensor_registry", "1.0.0", "root.platform.data-sources.mqtt-temperature-lab");
        var primitive = migration("platform_primitive_schema", "1.0.0", "root.platform.data-sources.platform-primitive");

        assertTrue(MigrationObjectService.matchesPendingFilter(
                mqtt,
                "1.0.0",
                "root.platform.data-sources.mqtt-temperature-lab"
        ));
        assertFalse(MigrationObjectService.matchesPendingFilter(
                mqtt,
                "1.0.0",
                "root.platform.data-sources.platform-primitive"
        ));
        assertFalse(MigrationObjectService.matchesPendingFilter(
                primitive,
                "1.0.0",
                "root.platform.data-sources.mqtt-temperature-lab"
        ));
        assertTrue(MigrationObjectService.matchesPendingFilter(
                primitive,
                "1.0.0",
                "root.platform.data-sources.platform-primitive"
        ));
    }

    @Test
    void matchesPendingFilterWithoutDataSourceIsVersionOnly() {
        var mqtt = migration("sensor_registry", "1.0.0", "root.platform.data-sources.mqtt-temperature-lab");
        assertTrue(MigrationObjectService.matchesPendingFilter(mqtt, "1.0.0", null));
        assertFalse(MigrationObjectService.matchesPendingFilter(mqtt, "2.0.0", null));
    }
}
