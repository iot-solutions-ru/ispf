package com.ispf.server.binding;

import com.ispf.core.object.Variable;
import com.ispf.server.datasource.DataSourceObjectService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.persistence.ObjectEntityMapper;
import com.ispf.server.persistence.ObjectVariableRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SqlBindingFanOutIsolationTest {

    private static final String DATA_SOURCE = "sql-binding-fan-out";
    private static final String TARGET = "root.platform";
    private static final String MISSING_TABLE_QUERY = "SELECT v FROM sql_binding_fan_out_missing";

    @Autowired
    private DataSourceObjectService dataSourceObjectService;
    @Autowired
    private SqlBindingObjectService sqlBindingObjectService;
    @Autowired
    private BindingRefreshAfterCommit bindingRefreshAfterCommit;
    @Autowired
    private ObjectManager objectManager;
    @Autowired
    private ObjectVariableRepository variableRepository;
    @Autowired
    private ObjectEntityMapper entityMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void failingQueryAfterANestedFunctionCommitMarksItsTargetBadWithoutWaitingForTheCaller() {
        String dataSourcePath = dataSourcePath();
        bind("nested-a", "nestedA", "SELECT 42 AS v", "on_function_success", "nestedProbe", dataSourcePath, true);
        bind("nested-b", "nestedB", MISSING_TABLE_QUERY, "on_function_success", "nestedProbe", dataSourcePath, true);
        bind("nested-c", "nestedC", "SELECT 7 AS v", "on_function_success", "nestedProbe", dataSourcePath, true);
        TransactionTemplate function = new TransactionTemplate(transactionManager);
        function.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        new TransactionTemplate(transactionManager).executeWithoutResult(caller -> {
            jdbcTemplate.update(
                    "UPDATE object_nodes SET description = description WHERE path = ?",
                    SqlBindingObjectService.BINDINGS_ROOT
            );
            function.executeWithoutResult(status ->
                    bindingRefreshAfterCommit.scheduleRefreshAfterFunction(TARGET, "nestedProbe"));
        });

        assertThat(live("nestedA")).containsEntry("value", 42.0).doesNotContainKey("quality");
        assertThat(live("nestedB")).containsEntry("quality", "BAD");
        assertThat(live("nestedC")).containsEntry("value", 7.0).doesNotContainKey("quality");
    }

    @Test
    void failingQueryAfterAFunctionWithoutATransactionMarksItsTargetBadAndTheOtherBindingsArePersisted() {
        String dataSourcePath = dataSourcePath();
        bind("plain-a", "plainA", "SELECT 42 AS v", "on_function_success", "plainProbe", dataSourcePath, true);
        bind("plain-b", "plainB", MISSING_TABLE_QUERY, "on_function_success", "plainProbe", dataSourcePath, true);
        bind("plain-c", "plainC", "SELECT 7 AS v", "on_function_success", "plainProbe", dataSourcePath, true);

        bindingRefreshAfterCommit.scheduleRefreshAfterFunction(TARGET, "plainProbe");

        assertThat(persisted(TARGET, "plainA")).containsEntry("value", 42.0).doesNotContainKey("quality");
        assertThat(persisted(TARGET, "plainB")).containsEntry("quality", "BAD");
        assertThat(persisted(TARGET, "plainC")).containsEntry("value", 7.0).doesNotContainKey("quality");
        assertThat(persisted(SqlBindingObjectService.BINDINGS_ROOT + ".plain-b", "lastRefreshedAt").get("value"))
                .asString()
                .isNotBlank();
    }

    @Test
    void failingScheduledQueryMarksItsTargetBadWithoutUndoingTheBindingRefreshedBeforeIt() {
        String dataSourcePath = dataSourcePath();
        bind("scheduled-a", "scheduledA", "SELECT 42 AS v", "on_schedule", "unused", dataSourcePath, true);
        bind("scheduled-b", "scheduledB", MISSING_TABLE_QUERY, "on_schedule", "unused", dataSourcePath, true);
        bind("scheduled-c", "scheduledC", "SELECT 7 AS v", "on_schedule", "unused", dataSourcePath, true);
        try {
            sqlBindingObjectService.refreshScheduledBindings();

            assertThat(persisted(TARGET, "scheduledA")).containsEntry("value", 42.0).doesNotContainKey("quality");
            assertThat(persisted(TARGET, "scheduledB")).containsEntry("quality", "BAD");
            assertThat(persisted(TARGET, "scheduledC")).containsEntry("value", 7.0).doesNotContainKey("quality");
        } finally {
            bind("scheduled-a", "scheduledA", "SELECT 42 AS v", "on_schedule", "unused", dataSourcePath, false);
            bind("scheduled-b", "scheduledB", MISSING_TABLE_QUERY, "on_schedule", "unused", dataSourcePath, false);
            bind("scheduled-c", "scheduledC", "SELECT 7 AS v", "on_schedule", "unused", dataSourcePath, false);
        }
    }

    private String dataSourcePath() {
        dataSourceObjectService.ensureDataSource(DATA_SOURCE, "SQL binding fan-out", "app_sql_binding_fan_out", "");
        return dataSourceObjectService.pathForNodeName(DATA_SOURCE);
    }

    private void bind(
            String bindingId,
            String variable,
            String query,
            String refresh,
            String triggerFunction,
            String dataSourcePath,
            boolean enabled
    ) {
        sqlBindingObjectService.upsert(new SqlBindingObjectService.BindingDefinition(
                null,
                bindingId,
                TARGET,
                variable,
                dataSourcePath,
                query,
                "v",
                refresh,
                30_000L,
                TARGET,
                triggerFunction,
                enabled,
                null
        ));
    }

    private Map<String, Object> live(String variable) {
        return objectManager.tree().findByPath(TARGET)
                .flatMap(target -> target.getVariable(variable))
                .flatMap(Variable::value)
                .orElseThrow(() -> new AssertionError("no live value for " + TARGET + "." + variable))
                .firstRow();
    }

    private Map<String, Object> persisted(String objectPath, String variable) {
        return variableRepository.findByObjectPathAndName(objectPath, variable)
                .map(entityMapper::toVariable)
                .flatMap(Variable::value)
                .orElseThrow(() -> new AssertionError("no persisted value for " + objectPath + "." + variable))
                .firstRow();
    }
}
