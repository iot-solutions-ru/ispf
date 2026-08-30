package com.ispf.server.binding;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.server.datasource.DataSourceSqlSession;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.blueprint.SystemObjectStructureService;
import com.ispf.server.tenant.TenantLocalDataAccessGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SqlBindingObjectServiceMissingTargetTest {

    @Mock
    ObjectManager objectManager;
    @Mock
    SystemObjectStructureService structureService;
    @Mock
    DataSourceSqlSession dataSourceSqlSession;
    @Mock
    TenantLocalDataAccessGuard tenantLocalDataAccessGuard;
    @Mock
    JdbcTemplate jdbcTemplate;

    @Test
    void missingTargetObjectIsSoftFailed() {
        SqlBindingObjectService service = newService();
        SqlBindingObjectService.BindingDefinition binding = sampleBinding();

        doAnswer(invocation -> {
            Consumer<JdbcTemplate> action = invocation.getArgument(1);
            org.mockito.Mockito.when(jdbcTemplate.queryForList(anyString()))
                    .thenReturn(List.of(Map.of("value", 3.5)));
            action.accept(jdbcTemplate);
            return null;
        }).when(dataSourceSqlSession).runWithDataSource(eq("root.ds"), any());

        doThrow(new ObjectNotFoundException("root.missing.target"))
                .when(objectManager)
                .setSystemVariableValue(eq("root.missing.target"), eq("metric"), any(DataRecord.class));

        assertThatCode(() -> service.executeRefresh(binding)).doesNotThrowAnyException();
        verify(objectManager).setSystemVariableValue(eq("root.missing.target"), eq("metric"), any(DataRecord.class));
    }

    @Test
    void missingTargetVariableIsSoftFailed() {
        SqlBindingObjectService service = newService();
        SqlBindingObjectService.BindingDefinition binding = sampleBinding();

        doAnswer(invocation -> {
            Consumer<JdbcTemplate> action = invocation.getArgument(1);
            org.mockito.Mockito.when(jdbcTemplate.queryForList(anyString()))
                    .thenReturn(List.of(Map.of("value", 1.0)));
            action.accept(jdbcTemplate);
            return null;
        }).when(dataSourceSqlSession).runWithDataSource(eq("root.ds"), any());

        doThrow(new IllegalArgumentException("Unknown variable: metric"))
                .when(objectManager)
                .setSystemVariableValue(eq("root.missing.target"), eq("metric"), any(DataRecord.class));

        assertThatCode(() -> service.executeRefresh(binding)).doesNotThrowAnyException();
    }

    @Test
    void otherIllegalArgumentStillPropagates() {
        SqlBindingObjectService service = newService();
        SqlBindingObjectService.BindingDefinition binding = sampleBinding();

        doAnswer(invocation -> {
            Consumer<JdbcTemplate> action = invocation.getArgument(1);
            org.mockito.Mockito.when(jdbcTemplate.queryForList(anyString()))
                    .thenReturn(List.of(Map.of("value", 1.0)));
            action.accept(jdbcTemplate);
            return null;
        }).when(dataSourceSqlSession).runWithDataSource(eq("root.ds"), any());

        doThrow(new IllegalArgumentException("bad schema"))
                .when(objectManager)
                .setSystemVariableValue(eq("root.missing.target"), eq("metric"), any(DataRecord.class));

        assertThatThrownBy(() -> service.executeRefresh(binding))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bad schema");
    }

    @Test
    void isMissingVariableDetectsUnknownVariablePrefix() {
        assertThat(SqlBindingObjectService.isMissingVariable(
                new IllegalArgumentException("Unknown variable: foo"))).isTrue();
        assertThat(SqlBindingObjectService.isMissingVariable(
                new IllegalArgumentException("something else"))).isFalse();
    }

    private SqlBindingObjectService newService() {
        return new SqlBindingObjectService(
                objectManager,
                structureService,
                dataSourceSqlSession,
                tenantLocalDataAccessGuard
        );
    }

    private static SqlBindingObjectService.BindingDefinition sampleBinding() {
        return new SqlBindingObjectService.BindingDefinition(
                "root.platform.bindings.sample",
                "sample",
                "root.missing.target",
                "metric",
                "root.ds",
                "SELECT 1 AS value",
                "value",
                "on_schedule",
                30_000L,
                null,
                null,
                true,
                null
        );
    }
}
