package com.ispf.server.application.bundle;

import com.ispf.server.application.data.PlatformSqlCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationBundleSnapshotStoreTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private PlatformSqlCatalog platformSqlCatalog;

    private ApplicationBundleSnapshotStore store;

    @BeforeEach
    void setUp() {
        when(platformSqlCatalog.table("application_bundle_deployments"))
                .thenReturn("application_bundle_deployments");
        store = new ApplicationBundleSnapshotStore(jdbcTemplate, platformSqlCatalog);
    }

    @Test
    void recordDeploymentActiveDeactivatesOthersThenInsertsActive() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("demo"), eq("1.0.0")))
                .thenReturn(0);

        store.recordDeployment("demo", "1.0.0", "{}", null, true);

        verify(jdbcTemplate).update(
                eq("UPDATE application_bundle_deployments SET is_active = FALSE WHERE app_id = ?"),
                eq("demo")
        );
        verify(jdbcTemplate).update(
                contains("INSERT INTO application_bundle_deployments"),
                any(),
                eq("demo"),
                eq("1.0.0"),
                eq("{}"),
                eq(null),
                any(),
                eq(true)
        );
    }

    @Test
    void recordDeploymentInactiveDoesNotClearPreviousActive() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("demo"), eq("2.0.0")))
                .thenReturn(0);

        store.recordDeployment("demo", "2.0.0", "{\"v\":2}", null, false);

        verify(jdbcTemplate, never()).update(
                eq("UPDATE application_bundle_deployments SET is_active = FALSE WHERE app_id = ?"),
                eq("demo")
        );
        verify(jdbcTemplate).update(
                contains("INSERT INTO application_bundle_deployments"),
                any(),
                eq("demo"),
                eq("2.0.0"),
                eq("{\"v\":2}"),
                eq(null),
                any(),
                eq(false)
        );
    }

    @Test
    void fourArgRecordDeploymentDefaultsToActive() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("demo"), eq("1.0.0")))
                .thenReturn(0);

        store.recordDeployment("demo", "1.0.0", "{}", null);

        verify(jdbcTemplate).update(
                eq("UPDATE application_bundle_deployments SET is_active = FALSE WHERE app_id = ?"),
                eq("demo")
        );
        ArgumentCaptor<Boolean> activeCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(jdbcTemplate).update(
                contains("INSERT INTO application_bundle_deployments"),
                any(),
                eq("demo"),
                eq("1.0.0"),
                eq("{}"),
                eq(null),
                any(),
                activeCaptor.capture()
        );
        assertTrue(activeCaptor.getValue());
    }

    @Test
    void recordDeploymentInactiveUpdatesExistingRowWithoutDeactivateAll() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("demo"), eq("2.0.0")))
                .thenReturn(1);

        store.recordDeployment("demo", "2.0.0", "{\"v\":2}", null, false);

        verify(jdbcTemplate, never()).update(
                contains("SET is_active = FALSE WHERE app_id = ?"),
                eq("demo")
        );
        ArgumentCaptor<Boolean> activeCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(jdbcTemplate).update(
                contains("UPDATE application_bundle_deployments"),
                eq("{\"v\":2}"),
                eq(null),
                any(),
                activeCaptor.capture(),
                eq("demo"),
                eq("2.0.0")
        );
        assertFalse(activeCaptor.getValue());
    }
}
