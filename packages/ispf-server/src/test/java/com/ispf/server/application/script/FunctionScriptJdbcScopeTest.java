package com.ispf.server.application.script;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FunctionScriptJdbcScopeTest {

    @Test
    void activeOrUsesScopedTemplateWhileRun() {
        JdbcTemplate platform = new JdbcTemplate(mock(DataSource.class));
        JdbcTemplate scoped = new JdbcTemplate(mock(DataSource.class));
        assertThat(FunctionScriptJdbcScope.activeOr(platform)).isSameAs(platform);

        FunctionScriptJdbcScope.run(scoped, () ->
                assertThat(FunctionScriptJdbcScope.activeOr(platform)).isSameAs(scoped));

        assertThat(FunctionScriptJdbcScope.activeOr(platform)).isSameAs(platform);
    }
}
