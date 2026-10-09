package com.ispf.server.binding;

import com.ispf.core.object.Variable;
import com.ispf.server.datasource.DataSourceObjectService;
import com.ispf.server.persistence.ObjectEntityMapper;
import com.ispf.server.persistence.ObjectVariableRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SqlBindingFanOutIsolationTest {

    private static final String DATA_SOURCE = "sql-binding-fan-out";
    private static final String TARGET = "root.platform";
    private static final String TRIGGER_FUNCTION = "sqlBindingFanOutProbe";
    private static final String MISSING_TABLE_QUERY = "SELECT v FROM sql_binding_fan_out_missing";

    @Autowired
    private DataSourceObjectService dataSourceObjectService;
    @Autowired
    private SqlBindingObjectService sqlBindingObjectService;
    @Autowired
    private BindingRefreshAfterCommit bindingRefreshAfterCommit;
    @Autowired
    private ObjectVariableRepository variableRepository;
    @Autowired
    private ObjectEntityMapper entityMapper;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void failingQueryAfterACommittedFunctionMarksItsTargetBadAndTheOtherBindingsArePersisted() {
        String dataSourcePath = dataSourcePath();
        bind("after-commit-a", "afterCommitA", "SELECT 42 AS v", "on_function_success", dataSourcePath, true);
        bind("after-commit-b", "afterCommitB", MISSING_TABLE_QUERY, "on_function_success", dataSourcePath, true);
        bind("after-commit-c", "afterCommitC", "SELECT 7 AS v", "on_function_success", dataSourcePath, true);

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                bindingRefreshAfterCommit.scheduleRefreshAfterFunction(TARGET, TRIGGER_FUNCTION));

        assertThat(persisted(TARGET, "afterCommitA")).containsEntry("value", 42.0).doesNotContainKey("quality");
        assertThat(persisted(TARGET, "afterCommitB")).containsEntry("quality", "BAD");
        assertThat(persisted(TARGET, "afterCommitC")).containsEntry("value", 7.0).doesNotContainKey("quality");
        assertThat(persisted(SqlBindingObjectService.BINDINGS_ROOT + ".after-commit-b", "lastRefreshedAt").get("value"))
                .asString()
                .isNotBlank();
    }

    @Test
    void failingScheduledQueryMarksItsTargetBadWithoutUndoingTheBindingRefreshedBeforeIt() {
        String dataSourcePath = dataSourcePath();
        bind("scheduled-a", "scheduledA", "SELECT 42 AS v", "on_schedule", dataSourcePath, true);
        bind("scheduled-b", "scheduledB", MISSING_TABLE_QUERY, "on_schedule", dataSourcePath, true);
        bind("scheduled-c", "scheduledC", "SELECT 7 AS v", "on_schedule", dataSourcePath, true);
        try {
            sqlBindingObjectService.refreshScheduledBindings();

            assertThat(persisted(TARGET, "scheduledA")).containsEntry("value", 42.0).doesNotContainKey("quality");
            assertThat(persisted(TARGET, "scheduledB")).containsEntry("quality", "BAD");
            assertThat(persisted(TARGET, "scheduledC")).containsEntry("value", 7.0).doesNotContainKey("quality");
        } finally {
            bind("scheduled-a", "scheduledA", "SELECT 42 AS v", "on_schedule", dataSourcePath, false);
            bind("scheduled-b", "scheduledB", MISSING_TABLE_QUERY, "on_schedule", dataSourcePath, false);
            bind("scheduled-c", "scheduledC", "SELECT 7 AS v", "on_schedule", dataSourcePath, false);
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
                TRIGGER_FUNCTION,
                enabled,
                null
        ));
    }

    private Map<String, Object> persisted(String objectPath, String variable) {
        return variableRepository.findByObjectPathAndName(objectPath, variable)
                .map(entityMapper::toVariable)
                .flatMap(Variable::value)
                .orElseThrow(() -> new AssertionError("no persisted value for " + objectPath + "." + variable))
                .firstRow();
    }
}
