package com.ispf.server.binding;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.datasource.DataSourceSqlSession;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.AutomationMetricsRecorder;
import com.ispf.server.plugin.blueprint.SystemObjectStructureService;
import com.ispf.server.tenant.TenantLocalDataAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SqlBindingObjectServiceBadQualityTest {

    private static final String BINDING_PATH = SqlBindingObjectService.BINDINGS_ROOT + ".oee";
    private static final String TARGET = "root.platform.devices.line1";
    private static final String VARIABLE = "oee";

    @Mock
    ObjectManager objectManager;
    @Mock
    SystemObjectStructureService structureService;
    @Mock
    DataSourceSqlSession dataSourceSqlSession;
    @Mock
    TenantLocalDataAccessGuard tenantLocalDataAccessGuard;
    @Mock
    AutomationMetricsRecorder metricsRecorder;
    @Mock
    JdbcTemplate jdbcTemplate;

    private SqlBindingObjectService service;

    @BeforeEach
    void setUp() {
        service = new SqlBindingObjectService(
                objectManager,
                structureService,
                dataSourceSqlSession,
                tenantLocalDataAccessGuard,
                metricsRecorder
        );
    }

    @Test
    void emptyResultKeepsTheLastValueMarkedBad() {
        targetHolds(value(42.5));
        queryReturns(List.of());

        service.executeRefresh(binding());

        assertThat(writtenRecord().firstRow()).containsEntry("value", 42.5).containsEntry("quality", "BAD");
        verify(metricsRecorder).recordSqlBindingFailure(SqlBindingValues.Failure.NO_DATA);
        verify(objectManager).setVariableValue(eq(BINDING_PATH), eq("lastRefreshedAt"), any(DataRecord.class));
    }

    @Test
    void unparseableValueIsMarkedBadInsteadOfZero() {
        targetHolds(value(42.5));
        queryReturns(List.of(Map.of("value", "n/a")));

        service.executeRefresh(binding());

        assertThat(writtenRecord().firstRow()).containsEntry("value", 42.5).containsEntry("quality", "BAD");
        verify(metricsRecorder).recordSqlBindingFailure(SqlBindingValues.Failure.BAD_VALUE);
    }

    @Test
    void sqlNullIsMarkedBad() {
        targetHolds(value(42.5));
        Map<String, Object> row = new HashMap<>();
        row.put("value", null);
        queryReturns(List.of(row));

        service.executeRefresh(binding());

        assertThat(writtenRecord().firstRow()).containsEntry("value", 42.5).containsEntry("quality", "BAD");
        verify(metricsRecorder).recordSqlBindingFailure(SqlBindingValues.Failure.NO_DATA);
    }

    @Test
    void missingColumnIsMarkedBad() {
        targetHolds(value(42.5));
        queryReturns(List.of(Map.of("other", 1)));

        service.executeRefresh(binding());

        assertThat(writtenRecord().firstRow()).containsEntry("value", 42.5).containsEntry("quality", "BAD");
        verify(metricsRecorder).recordSqlBindingFailure(SqlBindingValues.Failure.NO_DATA);
    }

    @Test
    void repeatedFailureDoesNotRewriteTheBadValue() {
        targetHolds(SqlBindingValues.badQuality(value(42.5), FieldType.DOUBLE));
        queryReturns(List.of());

        service.executeRefresh(binding());

        verify(objectManager, never()).setSystemVariableValue(any(), any(), any());
        verify(metricsRecorder).recordSqlBindingFailure(SqlBindingValues.Failure.NO_DATA);
        verify(objectManager).setVariableValue(eq(BINDING_PATH), eq("lastRefreshedAt"), any(DataRecord.class));
    }

    @Test
    void numericTextInAnyColumnCaseIsWrittenAsANumber() {
        queryReturns(List.of(Map.of("VALUE", "12.5")));

        service.executeRefresh(binding());

        assertThat(writtenRecord().firstRow()).containsEntry("value", 12.5).doesNotContainKey("quality");
        verifyNoInteractions(metricsRecorder);
    }

    @Test
    void queryFailureIsCountedAndStillPropagates() {
        doThrow(new DataAccessResourceFailureException("db down"))
                .when(dataSourceSqlSession).runWithDataSource(eq("root.ds"), any());

        assertThatThrownBy(() -> service.executeRefresh(binding()))
                .isInstanceOf(DataAccessResourceFailureException.class);

        verify(metricsRecorder).recordSqlBindingFailure(SqlBindingValues.Failure.QUERY_FAILED);
        verify(objectManager, never()).setSystemVariableValue(any(), any(), any());
    }

    private void targetHolds(DataRecord previous) {
        PlatformObject target = new PlatformObject("id-line1", TARGET, ObjectType.DEVICE, "line1", "", null);
        target.addVariable(new Variable(VARIABLE, previous.schema(), true, false, previous));
        when(objectManager.require(TARGET)).thenReturn(target);
    }

    private void queryReturns(List<Map<String, Object>> rows) {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(rows);
        doAnswer(invocation -> {
            invocation.<Consumer<JdbcTemplate>>getArgument(1).accept(jdbcTemplate);
            return null;
        }).when(dataSourceSqlSession).runWithDataSource(eq("root.ds"), any());
    }

    private DataRecord writtenRecord() {
        ArgumentCaptor<DataRecord> record = ArgumentCaptor.forClass(DataRecord.class);
        verify(objectManager).setSystemVariableValue(eq(TARGET), eq(VARIABLE), record.capture());
        return record.getValue();
    }

    private static DataRecord value(double value) {
        DataSchema schema = DataSchema.builder("doubleValue").field("value", FieldType.DOUBLE).build();
        return DataRecord.single(schema, Map.of("value", value));
    }

    private static SqlBindingObjectService.BindingDefinition binding() {
        return new SqlBindingObjectService.BindingDefinition(
                BINDING_PATH,
                "oee",
                TARGET,
                VARIABLE,
                "root.ds",
                "SELECT oee AS value FROM kpi",
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
