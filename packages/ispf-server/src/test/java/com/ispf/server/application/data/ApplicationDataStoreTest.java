package com.ispf.server.application.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationDataStoreTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PlatformSqlCatalog platformSqlCatalog;

    private ApplicationDataStore store;

    @BeforeEach
    void setUp() {
        when(platformSqlCatalog.table(anyString())).thenAnswer(inv -> inv.getArgument(0));
        store = new ApplicationDataStore(jdbcTemplate, platformSqlCatalog);
    }

    @Test
    void queryForListRejectsNonSelectSql() {
        assertThrows(IllegalArgumentException.class, () -> store.queryForList("DELETE FROM demo_item"));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void queryForListAllowsSelectSql() {
        when(jdbcTemplate.queryForList("SELECT 1")).thenReturn(java.util.List.of());

        store.queryForList("SELECT 1");

        verify(jdbcTemplate).queryForList("SELECT 1");
        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    void deleteAppRemovesChildRowsBeforeApplications() {
        when(jdbcTemplate.update(anyString(), eq("demo-app"))).thenReturn(1);

        assertTrue(store.deleteApp("demo-app"));

        verify(jdbcTemplate, atLeastOnce()).update(eq("DELETE FROM application_bundle_deployments WHERE app_id = ?"), eq("demo-app"));
        verify(jdbcTemplate).update(eq("DELETE FROM applications WHERE app_id = ?"), eq("demo-app"));
    }

    @Test
    void deleteAppRejectsBlankId() {
        assertFalse(store.deleteApp("  "));
        verifyNoInteractions(jdbcTemplate);
    }
}
