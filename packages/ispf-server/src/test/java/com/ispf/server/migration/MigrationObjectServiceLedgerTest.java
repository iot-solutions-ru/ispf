package com.ispf.server.migration;

import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.datasource.DataSourceSqlSession;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.blueprint.SystemObjectStructureService;
import com.ispf.server.tenant.TenantLocalDataAccessGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MigrationObjectServiceLedgerTest {

    @Mock
    ObjectManager objectManager;
    @Mock
    SystemObjectStructureService structureService;
    @Mock
    DataSourceSqlSession dataSourceSqlSession;
    @Mock
    TenantLocalDataAccessGuard tenantLocalDataAccessGuard;
    @Mock
    ApplicationDataStore applicationDataStore;

    @Test
    void recordsAppliedMigrationWhenAppIsRegistered() {
        when(applicationDataStore.findApp("robot-fleet-automation"))
                .thenReturn(Optional.of(Map.of("app_id", "robot-fleet-automation")));
        when(applicationDataStore.isMigrationApplied("robot-fleet-automation", "1.0.0", "V16"))
                .thenReturn(false);

        MigrationObjectService service = new MigrationObjectService(
                objectManager,
                structureService,
                dataSourceSqlSession,
                tenantLocalDataAccessGuard,
                applicationDataStore
        );
        service.recordAppliedMigration(new MigrationObjectService.MigrationDefinition(
                "root.platform.migrations.V16",
                "V16",
                "1.0.0",
                "root.platform.data-sources.robot-fleet-automation",
                "CREATE TABLE t(id int)"
        ));

        verify(applicationDataStore).recordMigration(
                "robot-fleet-automation",
                "1.0.0",
                "V16",
                "CREATE TABLE t(id int)"
        );
    }

    @Test
    void skipsLedgerWhenAppIsUnknown() {
        when(applicationDataStore.findApp("missing")).thenReturn(Optional.empty());

        MigrationObjectService service = new MigrationObjectService(
                objectManager,
                structureService,
                dataSourceSqlSession,
                tenantLocalDataAccessGuard,
                applicationDataStore
        );
        service.recordAppliedMigration(new MigrationObjectService.MigrationDefinition(
                "root.platform.migrations.V16",
                "V16",
                "1.0.0",
                "root.platform.data-sources.missing",
                "SELECT 1"
        ));

        verify(applicationDataStore, never()).recordMigration(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }
}
