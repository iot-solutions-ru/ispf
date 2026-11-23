package com.ispf.server.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.jdbc.BadSqlGrammarException;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiExceptionHandlerSqlGrammarTest {

    @Test
    void syntaxErrorIsBadRequestWithTheDatabaseMessage() {
        SQLException sql = new SQLException("Syntax error in SQL statement \"select from\"", "42000");
        BadSqlGrammarException exception = new BadSqlGrammarException("task", "select from", sql);

        ProblemDetail detail = new ApiExceptionHandler().handleBadSqlGrammar(exception);

        assertEquals(400, detail.getStatus());
        assertEquals("Invalid request", detail.getTitle());
        assertEquals("Syntax error in SQL statement \"select from\"", detail.getDetail());
    }

    @Test
    void postgresRaiseKeepsTheDatabaseRaiseTitle() {
        SQLException sql = new SQLException("rule rejected the row", "P0001");
        BadSqlGrammarException exception = new BadSqlGrammarException("task", "select 1", sql);

        ProblemDetail detail = new ApiExceptionHandler().handleBadSqlGrammar(exception);

        assertEquals(400, detail.getStatus());
        assertEquals("Database raise", detail.getTitle());
        assertEquals("rule rejected the row", detail.getDetail());
    }
}
