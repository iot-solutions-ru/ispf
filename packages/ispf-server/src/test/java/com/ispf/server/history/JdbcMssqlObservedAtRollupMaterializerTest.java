package com.ispf.server.history;

import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.MssqlRelationalProperties;
import com.ispf.server.config.VariableHistoryProperties;
import com.ispf.server.relational.MssqlDialect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class JdbcMssqlObservedAtRollupMaterializerTest {

    @Mock
    JdbcTemplate jdbcTemplate;
    @Mock
    PlatformTransactionManager transactionManager;
    @Mock
    ClusterProperties cluster;

    @Test
    void disabledByDefaultEvenOnSingleEngine() {
        MssqlRelationalProperties mssql = new MssqlRelationalProperties();
        mssql.setSingleEngine(true);
        mssql.setObservedRollupMaterializerEnabled(false);
        VariableHistoryProperties history = new VariableHistoryProperties();
        history.setEnabled(true);
        history.setStore("jdbc");

        var materializer = new JdbcMssqlObservedAtRollupMaterializer(
                jdbcTemplate, mssql, history, cluster, new MssqlDialect(), transactionManager
        );
        assertThat(materializer.isEnabled()).isFalse();
        assertThat(materializer.materializeTick().status()).isEqualTo("skipped");
    }

    @Test
    void enabledWhenFlagOn() {
        MssqlRelationalProperties mssql = new MssqlRelationalProperties();
        mssql.setSingleEngine(true);
        mssql.setObservedRollupMaterializerEnabled(true);
        VariableHistoryProperties history = new VariableHistoryProperties();
        history.setEnabled(true);
        history.setStore("jdbc");

        var materializer = new JdbcMssqlObservedAtRollupMaterializer(
                jdbcTemplate, mssql, history, cluster, new MssqlDialect(), transactionManager
        );
        assertThat(materializer.isEnabled()).isTrue();
    }
}
