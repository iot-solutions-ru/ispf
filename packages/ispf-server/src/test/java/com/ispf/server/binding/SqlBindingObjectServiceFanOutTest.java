package com.ispf.server.binding;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectTree;
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
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SqlBindingObjectServiceFanOutTest {

    private static final String TARGET = "root.platform.devices.line1";
    private static final String HUB = "root.platform.singleton-blueprints.kpi-hub";
    private static final String BROKEN_QUERY = "SELECT oee AS value FROM missing_kpi";

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
    ObjectTree objectTree;
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
        when(objectManager.tree()).thenReturn(objectTree);
        when(objectTree.findByPath(SqlBindingObjectService.BINDINGS_ROOT)).thenReturn(Optional.of(new PlatformObject(
                "id-bindings", SqlBindingObjectService.BINDINGS_ROOT, ObjectType.BINDINGS, "bindings", "", null)));
        doAnswer(invocation -> {
            invocation.<Consumer<JdbcTemplate>>getArgument(1).accept(jdbcTemplate);
            return null;
        }).when(dataSourceSqlSession).runWithDataSource(eq("root.ds"), any());
        lenient().when(jdbcTemplate.queryForList(BROKEN_QUERY)).thenThrow(new BadSqlGrammarException(
                "StatementCallback", BROKEN_QUERY, new SQLException("relation \"missing_kpi\" does not exist")));
    }

    @Test
    void scheduledRefreshMarksAFailingQueryBadAndStillRefreshesTheOtherBindings() {
        bindings(
                binding("a", "SELECT 1.5 AS value", "on_schedule"),
                binding("b", BROKEN_QUERY, "on_schedule"),
                binding("c", "SELECT 2.5 AS value", "on_schedule")
        );
        queryReturns("SELECT 1.5 AS value", 1.5);
        queryReturns("SELECT 2.5 AS value", 2.5);
        targetHolds("b", 40.0);

        service.refreshScheduledBindings();

        assertThat(written("a").firstRow()).containsEntry("value", 1.5).doesNotContainKey("quality");
        assertThat(written("b").firstRow()).containsEntry("value", 40.0).containsEntry("quality", "BAD");
        assertThat(written("c").firstRow()).containsEntry("value", 2.5).doesNotContainKey("quality");
        verify(metricsRecorder).recordSqlBindingFailure(SqlBindingValues.Failure.QUERY_FAILED);
        for (String name : List.of("a", "b", "c")) {
            verify(objectManager).setVariableValue(
                    eq(SqlBindingObjectService.BINDINGS_ROOT + "." + name), eq("lastRefreshedAt"), any());
        }
    }

    @Test
    void unexpectedFailureOfOneBindingDoesNotStopTheScheduledRefresh() {
        bindings(
                binding("a", "SELECT 1.5 AS value", "on_schedule"),
                binding("b", "SELECT 2.5 AS value", "on_schedule")
        );
        queryReturns("SELECT 1.5 AS value", 1.5);
        queryReturns("SELECT 2.5 AS value", 2.5);
        doThrow(new IllegalStateException("storage unavailable"))
                .when(objectManager).setSystemVariableValue(eq(TARGET), eq("a"), any(DataRecord.class));

        service.refreshScheduledBindings();

        assertThat(written("b").firstRow()).containsEntry("value", 2.5);
        verify(objectManager, never()).setVariableValue(
                eq(SqlBindingObjectService.BINDINGS_ROOT + ".a"), eq("lastRefreshedAt"), any());
    }

    @Test
    void refreshAfterAFunctionCommitMarksAFailingQueryBadAndStillRefreshesTheOtherBindings() {
        bindings(
                binding("a", BROKEN_QUERY, "on_function_success"),
                binding("b", "SELECT 2.5 AS value", "on_function_success")
        );
        queryReturns("SELECT 2.5 AS value", 2.5);
        targetHolds("a", 40.0);

        service.refreshAfterFunctionCommit(HUB, "recalc");

        assertThat(written("a").firstRow()).containsEntry("value", 40.0).containsEntry("quality", "BAD");
        assertThat(written("b").firstRow()).containsEntry("value", 2.5);
        verify(metricsRecorder).recordSqlBindingFailure(SqlBindingValues.Failure.QUERY_FAILED);
    }

    @Test
    void refreshInsideTheWorkflowTransactionStillFailsOnAFailingQuery() {
        bindings(
                binding("a", BROKEN_QUERY, "on_function_success"),
                binding("b", "SELECT 2.5 AS value", "on_function_success")
        );

        assertThatThrownBy(() -> service.refreshAfterFunction(HUB, "recalc"))
                .isInstanceOf(BadSqlGrammarException.class);

        verify(metricsRecorder).recordSqlBindingFailure(SqlBindingValues.Failure.QUERY_FAILED);
        verify(objectManager, never()).setSystemVariableValue(any(), any(), any());
    }

    private void bindings(PlatformObject... nodes) {
        when(objectTree.childrenOf(SqlBindingObjectService.BINDINGS_ROOT)).thenReturn(List.of(nodes));
    }

    private void queryReturns(String query, double value) {
        when(jdbcTemplate.queryForList(query)).thenReturn(List.of(Map.of("value", value)));
    }

    private void targetHolds(String variable, double value) {
        PlatformObject target = new PlatformObject("id-line1", TARGET, ObjectType.DEVICE, "line1", "", null);
        DataSchema schema = DataSchema.builder("doubleValue").field("value", FieldType.DOUBLE).build();
        target.addVariable(new Variable(variable, schema, true, false, DataRecord.single(schema, Map.of("value", value))));
        when(objectManager.require(TARGET)).thenReturn(target);
    }

    private DataRecord written(String variable) {
        ArgumentCaptor<DataRecord> record = ArgumentCaptor.forClass(DataRecord.class);
        verify(objectManager).setSystemVariableValue(eq(TARGET), eq(variable), record.capture());
        return record.getValue();
    }

    private static PlatformObject binding(String name, String query, String refresh) {
        String path = SqlBindingObjectService.BINDINGS_ROOT + "." + name;
        PlatformObject node = new PlatformObject("id-" + name, path, ObjectType.BINDING, name, "", null);
        node.addVariable(stringVar("targetObjectPath", TARGET));
        node.addVariable(stringVar("variable", name));
        node.addVariable(stringVar("dataSourcePath", "root.ds"));
        node.addVariable(stringVar("query", query));
        node.addVariable(stringVar("valueField", "value"));
        node.addVariable(stringVar("refresh", refresh));
        node.addVariable(stringVar("triggerObjectPath", HUB));
        node.addVariable(stringVar("triggerFunctionName", "recalc"));
        DataSchema enabled = DataSchema.builder("enabled").field("value", FieldType.BOOLEAN).build();
        node.addVariable(new Variable("enabled", enabled, true, true, DataRecord.single(enabled, Map.of("value", true))));
        return node;
    }

    private static Variable stringVar(String name, String value) {
        DataSchema schema = DataSchema.builder(name).field("value", FieldType.STRING).build();
        return new Variable(name, schema, true, true, DataRecord.single(schema, Map.of("value", value)));
    }
}
