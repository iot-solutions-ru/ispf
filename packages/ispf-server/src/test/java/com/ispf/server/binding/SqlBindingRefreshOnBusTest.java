package com.ispf.server.binding;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.Variable;
import com.ispf.server.datasource.DataSourceObjectService;
import com.ispf.server.persistence.ObjectEntityMapper;
import com.ispf.server.persistence.ObjectVariableRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "ispf.object-change.async-enabled=true")
class SqlBindingRefreshOnBusTest {

    private static final String DATA_SOURCE = "sql-binding-on-bus";
    private static final String TARGET = "root.platform";
    private static final String FUNCTION = "busProbe";

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
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void bindingsRefreshedAfterANestedFunctionCommitAreSaved() throws InterruptedException {
        String dataSourcePath = dataSourcePath();
        bind("bus-a", "busA", "SELECT 42 AS v", dataSourcePath);
        bind("bus-b", "busB", "SELECT v FROM sql_binding_on_bus_missing", dataSourcePath);
        bind("bus-c", "busC", "SELECT 7 AS v", dataSourcePath);
        TransactionTemplate function = new TransactionTemplate(transactionManager);
        function.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        new TransactionTemplate(transactionManager).executeWithoutResult(caller -> {
            jdbcTemplate.update(
                    "UPDATE object_nodes SET description = description WHERE path = ?",
                    SqlBindingObjectService.BINDINGS_ROOT
            );
            function.executeWithoutResult(status ->
                    bindingRefreshAfterCommit.scheduleRefreshAfterFunction(TARGET, FUNCTION));
        });

        assertThat(awaitPersisted("busA", row -> Double.valueOf(42.0).equals(row.get("value"))))
                .containsEntry("value", 42.0)
                .doesNotContainKey("quality");
        assertThat(awaitPersisted("busB", row -> "BAD".equals(row.get("quality"))))
                .containsEntry("quality", "BAD");
        assertThat(awaitPersisted("busC", row -> Double.valueOf(7.0).equals(row.get("value"))))
                .containsEntry("value", 7.0)
                .doesNotContainKey("quality");
    }

    private String dataSourcePath() {
        dataSourceObjectService.ensureDataSource(DATA_SOURCE, "SQL binding on bus", "app_sql_binding_on_bus", "");
        return dataSourceObjectService.pathForNodeName(DATA_SOURCE);
    }

    private void bind(String bindingId, String variable, String query, String dataSourcePath) {
        sqlBindingObjectService.upsert(new SqlBindingObjectService.BindingDefinition(
                null,
                bindingId,
                TARGET,
                variable,
                dataSourcePath,
                query,
                "v",
                "on_function_success",
                30_000L,
                TARGET,
                FUNCTION,
                true,
                null
        ));
    }

    private Map<String, Object> awaitPersisted(String variable, Predicate<Map<String, Object>> refreshed)
            throws InterruptedException {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (true) {
            Map<String, Object> row = variableRepository.findByObjectPathAndName(TARGET, variable)
                    .map(entityMapper::toVariable)
                    .flatMap(Variable::value)
                    .map(DataRecord::firstRow)
                    .orElse(Map.of());
            if (refreshed.test(row) || System.nanoTime() >= deadline) {
                return row;
            }
            Thread.sleep(20);
        }
    }
}
