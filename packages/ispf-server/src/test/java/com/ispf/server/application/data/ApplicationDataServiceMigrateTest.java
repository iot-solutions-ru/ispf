package com.ispf.server.application.data;

import com.ispf.server.application.tree.ApplicationObjectTreeService;
import com.ispf.server.datasource.DataSourceObjectService;
import com.ispf.server.migration.MigrationLedger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationDataServiceMigrateTest {

    @Mock
    ApplicationDataStore store;
    @Mock
    ApplicationSchemaSession schemaSession;
    @Mock
    ApplicationObjectTreeService objectTreeService;
    @Mock
    DataSourceObjectService dataSourceObjectService;

    @Test
    void unchangedScriptIsSkippedWhenTheVersionChanges() {
        when(store.findApp("plant")).thenReturn(Optional.of(Map.of("schema_name", "app_plant")));
        when(store.appliedMigrationChecksum("plant", "init")).thenReturn(MigrationLedger.checksum("SELECT 1"));

        Map<String, Object> result = service().migrate(
                "plant",
                "2.0.0",
                List.of(new ApplicationDataService.MigrationScript("init", "SELECT 1"))
        );

        assertEquals(List.of("init"), result.get("skipped"));
        verify(schemaSession, never()).runInSchema(any(), any());
    }

    @Test
    void editedScriptIsRejectedBeforeSql() {
        when(store.findApp("plant")).thenReturn(Optional.of(Map.of("schema_name", "app_plant")));
        when(store.appliedMigrationChecksum("plant", "init")).thenReturn(MigrationLedger.checksum("SELECT 1"));

        assertThrows(IllegalStateException.class, () -> service().migrate(
                "plant",
                "2.0.0",
                List.of(new ApplicationDataService.MigrationScript("init", "SELECT 2"))
        ));
        verify(schemaSession, never()).runInSchema(any(), any());
    }

    private ApplicationDataService service() {
        return new ApplicationDataService(store, schemaSession, objectTreeService, dataSourceObjectService);
    }
}
