package com.ispf.server.application.binding;

import com.ispf.server.application.data.PlatformSqlCatalog;
import com.ispf.server.relational.MssqlDialect;
import com.ispf.server.relational.MysqlDialect;
import com.ispf.server.relational.PostgreSqlDialect;
import com.ispf.server.relational.RelationalDialect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ApplicationSqlBindingStoreDialectSqlTest {

    private static final String APP = "app";
    private static final String HUB = "root.platform.singletons.hub";

    @Mock
    JdbcTemplate jdbcTemplate;
    @Captor
    ArgumentCaptor<String> sqlCaptor;

    @Test
    void functionSuccessTriggerListUsesConcatOnMysql() {
        assertThat(functionSuccessSql(new MysqlDialect()))
                .contains("CONCAT(',', trigger_function_name, ',') LIKE CONCAT('%,', ?, ',%')")
                .doesNotContain("||");
    }

    @Test
    void functionSuccessTriggerListUsesPlusOnMssql() {
        assertThat(functionSuccessSql(new MssqlDialect()))
                .contains("(',' + trigger_function_name + ',') LIKE ('%,' + ? + ',%')")
                .doesNotContain("||");
    }

    @Test
    void functionSuccessTriggerListKeepsPipesOnPostgres() {
        assertThat(functionSuccessSql(new PostgreSqlDialect()))
                .contains("(',' || trigger_function_name || ',') LIKE ('%,' || ? || ',%')");
    }

    private String functionSuccessSql(RelationalDialect dialect) {
        new ApplicationSqlBindingStore(jdbcTemplate, new PlatformSqlCatalog(dialect), dialect)
                .listForFunctionSuccess(APP, HUB, "recalc");
        verify(jdbcTemplate).query(
                sqlCaptor.capture(), any(RowMapper.class),
                eq(true), eq(APP), eq("recalc"), eq(HUB), eq("recalc"), eq(HUB), eq("recalc")
        );
        return sqlCaptor.getValue().replaceAll("\\s+", " ");
    }
}
