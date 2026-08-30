package com.ispf.server.application.binding;

import com.ispf.server.application.data.PlatformSqlCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationSqlBindingStoreDisableSubtreeTest {

    @Mock
    JdbcTemplate jdbcTemplate;
    @Mock
    PlatformSqlCatalog platformSqlCatalog;

    private ApplicationSqlBindingStore store;

    @BeforeEach
    void setUp() {
        when(platformSqlCatalog.table("application_sql_bindings")).thenReturn("application_sql_bindings");
        store = new ApplicationSqlBindingStore(jdbcTemplate, platformSqlCatalog);
    }

    @Test
    void disableForObjectSubtreeUpdatesExactAndDescendantPaths() {
        when(jdbcTemplate.update(anyString(), eq("root.devices.a"), eq("root.devices.a.%"))).thenReturn(2);

        int disabled = store.disableForObjectSubtree("root.devices.a");

        assertThat(disabled).isEqualTo(2);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), eq("root.devices.a"), eq("root.devices.a.%"));
        String sql = sqlCaptor.getValue().replaceAll("\\s+", " ").toLowerCase();
        assertThat(sql).contains("set enabled = false");
        assertThat(sql).contains("object_path = ? or object_path like ?");
    }

    @Test
    void disableForObjectSubtreeIgnoresBlankPath() {
        assertThat(store.disableForObjectSubtree(" ")).isZero();
        assertThat(store.disableForObjectSubtree(null)).isZero();
        verify(jdbcTemplate, never()).update(anyString(), any(), any());
    }
}
