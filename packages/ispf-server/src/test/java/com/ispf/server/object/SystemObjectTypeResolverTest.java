package com.ispf.server.object;

import com.ispf.core.object.ObjectType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemObjectTypeResolverTest {

    @Test
    void catalogFoldersKeepTheirType() {
        assertEquals(ObjectType.MIGRATIONS, SystemObjectTypeResolver.resolve("root.platform.migrations", null).orElseThrow());
        assertEquals(
                ObjectType.MIGRATIONS,
                SystemObjectTypeResolver.resolve("root.tenant.acme.platform.migrations", null).orElseThrow()
        );
        assertEquals(
                ObjectType.MIGRATIONS,
                SystemObjectTypeResolver.resolve("root.platform.applications.demo.migrations", null).orElseThrow()
        );
        assertEquals(
                ObjectType.FUNCTIONS,
                SystemObjectTypeResolver.resolve("root.tenant.acme.platform.applications.demo.functions", null).orElseThrow()
        );
    }

    @Test
    void anObjectNamedMigrationsIsNotACatalogFolder() {
        assertTrue(SystemObjectTypeResolver.resolve(
                "root.platform.singleton-blueprints.migrations",
                "e5b09bc1-7265-4dff-b431-d2f92e567ec6"
        ).isEmpty());
        assertTrue(SystemObjectTypeResolver.resolve(
                "root.platform.instance-types.reports",
                null
        ).isEmpty());
        assertTrue(SystemObjectTypeResolver.resolve(
                "root.platform.applications.demo.migrations.v1",
                null
        ).isEmpty());
    }
}
