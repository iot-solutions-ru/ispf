package com.ispf.server.application.data;

import com.ispf.server.application.tree.ApplicationObjectTreeService;
import com.ispf.server.datasource.DataSourceObjectService;
import com.ispf.server.migration.MigrationLedger;
import com.ispf.server.relational.H2Dialect;
import com.ispf.server.relational.MssqlDialect;
import com.ispf.server.relational.RelationalDialect;
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
import static org.mockito.ArgumentMatchers.eq;
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

        Map<String, Object> result = service(new H2Dialect()).migrate(
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

        assertThrows(IllegalStateException.class, () -> service(new H2Dialect()).migrate(
                "plant",
                "2.0.0",
                List.of(new ApplicationDataService.MigrationScript("init", "SELECT 2"))
        ));
        verify(schemaSession, never()).runInSchema(any(), any());
    }

    @Test
    void registerOnMssqlSkipsAppSchemaCreate() {
        when(store.findApp("mssql-b1-cert")).thenReturn(Optional.of(Map.of(
                "schema_name", "app_mssql_b1_cert",
                "table_prefix", ""
        )));
        when(dataSourceObjectService.pathForNodeName("mssql-b1-cert"))
                .thenReturn("root.platform.data-sources.mssql-b1-cert");

        service(new MssqlDialect()).register(
                "mssql-b1-cert",
                "MSSQL B1 Certification",
                "",
                "app_mssql_b1_cert"
        );

        verify(schemaSession, never()).ensureSchemaExists(any());
        verify(dataSourceObjectService).ensureDataSource(
                eq("mssql-b1-cert"),
                eq("MSSQL B1 Certification"),
                eq("app_mssql_b1_cert"),
                any()
        );
        verify(objectTreeService).syncApplication("mssql-b1-cert");
    }

    private ApplicationDataService service(RelationalDialect dialect) {
        return new ApplicationDataService(
                store, schemaSession, objectTreeService, dataSourceObjectService, dialect
        );
    }
}
