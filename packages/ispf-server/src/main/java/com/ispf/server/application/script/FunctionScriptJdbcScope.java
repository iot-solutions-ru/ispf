package com.ispf.server.application.script;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Per-invoke JDBC for script SQL steps. {@link com.ispf.server.function.ScriptFunctionHandler}
 * sets the data-source template while {@link FunctionScriptEngine} runs select/exec steps.
 */
public final class FunctionScriptJdbcScope {

    private static final ThreadLocal<JdbcTemplate> ACTIVE = new ThreadLocal<>();

    private FunctionScriptJdbcScope() {
    }

    public static JdbcTemplate activeOr(JdbcTemplate platformDefault) {
        JdbcTemplate scoped = ACTIVE.get();
        return scoped != null ? scoped : platformDefault;
    }

    public static void run(JdbcTemplate jdbc, Runnable action) {
        ACTIVE.set(jdbc);
        try {
            action.run();
        } finally {
            ACTIVE.remove();
        }
    }
}
