package com.ispf.server.application.binding;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.alert.AlertRuleService;
import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.application.data.ApplicationSchemaSession;
import com.ispf.server.binding.BindingInvokeAuditService;
import com.ispf.server.binding.SqlBindingValues;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.persistence.ObjectEntityMapper;
import com.ispf.server.platform.AutomationMetricsRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.BadSqlGrammarException;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationSqlBindingServiceBadQualityTest {

    private static final UUID ID = UUID.randomUUID();
    private static final String TARGET = "root.platform.devices.line1";
    private static final String VARIABLE = "oee";

    @Mock
    ApplicationSqlBindingStore store;
    @Mock
    ApplicationSchemaSession schemaSession;
    @Mock
    ApplicationDataStore dataStore;
    @Mock
    ObjectManager objectManager;
    @Mock
    AlertRuleService alertRuleService;
    @Mock
    BindingInvokeAuditService bindingAuditService;
    @Mock
    ObjectEntityMapper entityMapper;
    @Mock
    ApplicationSqlBindingEventIndex sqlBindingEventIndex;
    @Mock
    AutomationMetricsRecorder metricsRecorder;
    @Mock
    ObjectTree objectTree;

    private ApplicationSqlBindingService service;

    @BeforeEach
    void setUp() {
        service = new ApplicationSqlBindingService(
                store,
                schemaSession,
                dataStore,
                objectManager,
                alertRuleService,
                bindingAuditService,
                entityMapper,
                sqlBindingEventIndex,
                metricsRecorder
        );
        lenient().when(dataStore.findApp("demo")).thenReturn(Optional.empty());
        lenient().doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return null;
        }).when(schemaSession).runInSchema(anyString(), any(Runnable.class));
        lenient().doAnswer(invocation -> invocation.getArgument(0, Supplier.class).get())
                .when(schemaSession).callWithPlatformCatalog(any());
        lenient().doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(schemaSession).runWithPlatformCatalog(any(Runnable.class));
        lenient().when(objectManager.tree()).thenReturn(objectTree);
        lenient().when(entityMapper.auditDiff(any(), any())).thenReturn("{}");
    }

    @Test
    void emptyResultKeepsTheLastValueMarkedBadAndAuditsTheFailure() {
        targetHolds(doubleValue(42.5));
        when(dataStore.queryForList(anyString())).thenReturn(List.of());

        service.refreshBinding(binding("value"));

        assertThat(writtenRecord().firstRow()).containsEntry("value", 42.5).containsEntry("quality", "BAD");
        verify(metricsRecorder).recordSqlBindingFailure(SqlBindingValues.Failure.NO_DATA);
        verify(store).markRefreshed(ID);
        verify(alertRuleService).processVariableChange(TARGET, VARIABLE);
        verifyAudit(false, true, "query returned no rows");
    }

    @Test
    void unparseableValueIsMarkedBadInsteadOfZero() {
        targetHolds(doubleValue(42.5));
        when(dataStore.queryForList(anyString())).thenReturn(List.of(Map.of("value", "n/a")));

        service.refreshBinding(binding("value"));

        assertThat(writtenRecord().firstRow()).containsEntry("value", 42.5).containsEntry("quality", "BAD");
        verify(metricsRecorder).recordSqlBindingFailure(SqlBindingValues.Failure.BAD_VALUE);
    }

    @Test
    void emptyResultOfATextBindingIsNotWrittenAsZero() {
        DataSchema schema = DataSchema.builder("sqlBindingValue").field("value", FieldType.STRING).build();
        targetHolds(DataRecord.single(schema, Map.of("value", "RUNNING")));
        when(dataStore.queryForList(anyString())).thenReturn(List.of());

        service.refreshBinding(binding("status"));

        assertThat(writtenRecord().firstRow()).containsEntry("value", "RUNNING").containsEntry("quality", "BAD");
    }

    @Test
    void repeatedFailureIsAuditedWithoutRewritingTheValue() {
        targetHolds(SqlBindingValues.badQuality(doubleValue(42.5), FieldType.DOUBLE));
        when(dataStore.queryForList(anyString())).thenReturn(List.of());

        service.refreshBinding(binding("value"));

        verify(objectManager, never()).setSystemVariableValue(any(), any(), any());
        verify(alertRuleService, never()).processVariableChange(any(), any());
        verify(store).markRefreshed(ID);
        verifyAudit(false, false, "query returned no rows");
    }

    @Test
    void numericTextIsWrittenAsANumber() {
        when(objectTree.findByPath(TARGET)).thenReturn(Optional.empty());
        when(dataStore.queryForList(anyString())).thenReturn(List.of(Map.of("value", " 12.5 ")));

        service.refreshBinding(binding("value"));

        assertThat(writtenRecord().firstRow()).containsEntry("value", 12.5).doesNotContainKey("quality");
        verifyNoInteractions(metricsRecorder);
        verifyAudit(true, true, null);
    }

    @Test
    void failingQueryOfAnEventRefreshKeepsTheLastValueMarkedBadAndAuditsTheFailure() {
        targetHolds(doubleValue(42.5));
        when(dataStore.queryForList(anyString())).thenThrow(new BadSqlGrammarException(
                "StatementCallback", "SELECT oee AS value FROM kpi", new SQLException("relation \"kpi\" does not exist")));

        service.refreshBinding(binding("value"));

        assertThat(writtenRecord().firstRow()).containsEntry("value", 42.5).containsEntry("quality", "BAD");
        verify(metricsRecorder).recordSqlBindingFailure(SqlBindingValues.Failure.QUERY_FAILED);
        verify(store).markRefreshed(ID);
        verify(alertRuleService).processVariableChange(TARGET, VARIABLE);
        verifyAudit(false, true, "query failed: relation \"kpi\" does not exist");
    }

    @Test
    void manualRefreshStillPropagatesAFailingQuery() {
        when(store.listByApp("demo")).thenReturn(List.of(binding("value")));
        when(dataStore.queryForList(anyString())).thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(() -> service.refresh("demo", TARGET, VARIABLE))
                .isInstanceOf(DataAccessResourceFailureException.class);

        verify(metricsRecorder).recordSqlBindingFailure(SqlBindingValues.Failure.QUERY_FAILED);
        verify(objectManager, never()).setSystemVariableValue(any(), any(), any());
        verify(store, never()).markRefreshed(any());
        verifyAudit(false, false, "db down");
    }

    @Test
    void scheduledRefreshContinuesAfterAnUnexpectedFailureOfOneBinding() {
        UUID failingId = UUID.randomUUID();
        ApplicationSqlBindingStore.SqlBinding failing = binding(failingId, "root.platform.devices.line0");
        when(store.listEnabledForSchedule()).thenReturn(List.of(failing, binding("value")));
        when(objectTree.findByPath(anyString())).thenReturn(Optional.empty());
        when(dataStore.queryForList(anyString())).thenReturn(List.of(Map.of("value", 12.5)));
        doThrow(new IllegalStateException("storage unavailable")).when(store).markRefreshed(failingId);

        service.refreshScheduledBindings();

        assertThat(writtenRecord().firstRow()).containsEntry("value", 12.5);
        verify(store).markRefreshed(ID);
    }

    private void targetHolds(DataRecord previous) {
        PlatformObject target = new PlatformObject("id-line1", TARGET, ObjectType.DEVICE, "line1", "", null);
        target.addVariable(new Variable(VARIABLE, previous.schema(), true, false, previous));
        when(objectTree.findByPath(TARGET)).thenReturn(Optional.of(target));
    }

    private DataRecord writtenRecord() {
        ArgumentCaptor<DataRecord> record = ArgumentCaptor.forClass(DataRecord.class);
        verify(objectManager).setSystemVariableValue(eq(TARGET), eq(VARIABLE), record.capture());
        return record.getValue();
    }

    private void verifyAudit(boolean success, boolean changed, String error) {
        verify(bindingAuditService).recordSql(
                eq(TARGET),
                eq(ID.toString()),
                eq(VARIABLE),
                anyString(),
                eq(success),
                eq(changed),
                eq(error),
                anyLong(),
                any()
        );
    }

    private static DataRecord doubleValue(double value) {
        DataSchema schema = DataSchema.builder("sqlBindingValue").field("value", FieldType.DOUBLE).build();
        return DataRecord.single(schema, Map.of("value", value));
    }

    private static ApplicationSqlBindingStore.SqlBinding binding(String valueField) {
        return binding(ID, TARGET, valueField);
    }

    private static ApplicationSqlBindingStore.SqlBinding binding(UUID id, String objectPath) {
        return binding(id, objectPath, "value");
    }

    private static ApplicationSqlBindingStore.SqlBinding binding(UUID id, String objectPath, String valueField) {
        return new ApplicationSqlBindingStore.SqlBinding(
                id,
                "demo",
                objectPath,
                VARIABLE,
                "SELECT oee AS value FROM kpi",
                "on_schedule",
                30_000L,
                valueField,
                null,
                null,
                true,
                null
        );
    }
}
