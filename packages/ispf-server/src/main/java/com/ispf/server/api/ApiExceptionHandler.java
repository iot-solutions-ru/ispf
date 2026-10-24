package com.ispf.server.api;

import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.expression.ExpressionException;
import com.ispf.plugin.workflow.WorkflowException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;

@RestControllerAdvice
public class ApiExceptionHandler {

    /** PostgreSQL {@code RAISE EXCEPTION} / plpgsql raise_exception. */
    private static final String PG_RAISE_EXCEPTION = "P0001";

    @ExceptionHandler(ObjectNotFoundException.class)
    public ProblemDetail handleObjectNotFound(ObjectNotFoundException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        detail.setTitle("Object not found");
        return detail;
    }

    @ExceptionHandler(WorkflowException.class)
    public ProblemDetail handleWorkflow(WorkflowException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        detail.setTitle("Workflow error");
        return detail;
    }

    @ExceptionHandler(ExpressionException.class)
    public ProblemDetail handleExpression(ExpressionException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        detail.setTitle("Expression error");
        return detail;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableMessage(HttpMessageNotReadableException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Invalid JSON request body"
        );
        detail.setTitle("Invalid request");
        return detail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        detail.setTitle("Invalid request");
        return detail;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        detail.setTitle("Invalid state");
        return detail;
    }

    /**
     * Script / JDBC unique and other integrity failures must not surface as a bare 500.
     * Application script {@code exec}/{@code select*} steps propagate
     * {@link DataIntegrityViolationException} (often {@code DuplicateKeyException}) to the HTTP layer.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException exception) {
        String message = integrityDetail(exception);
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, message);
        detail.setTitle("Data integrity violation");
        return detail;
    }

    /**
     * Script SQL {@code RAISE EXCEPTION} must not surface as a bare Spring 500.
     * Other uncategorized SQL errors keep propagating as 500.
     */
    @ExceptionHandler(UncategorizedSQLException.class)
    public ProblemDetail handleUncategorizedSql(UncategorizedSQLException exception) {
        if (!isPostgresRaise(exception)) {
            throw exception;
        }
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, sqlRaiseDetail(exception));
        detail.setTitle("Database raise");
        return detail;
    }

    @ExceptionHandler(BadSqlGrammarException.class)
    public ProblemDetail handleBadSqlGrammar(BadSqlGrammarException exception) {
        if (!isPostgresRaise(exception)) {
            throw exception;
        }
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, sqlRaiseDetail(exception));
        detail.setTitle("Database raise");
        return detail;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String message = exception.getReason() != null ? exception.getReason() : exception.getMessage();
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle(status.getReasonPhrase());
        return detail;
    }

    static String integrityDetail(DataIntegrityViolationException exception) {
        Throwable root = exception.getMostSpecificCause();
        String rootMessage = root != null && root.getMessage() != null && !root.getMessage().isBlank()
                ? root.getMessage().trim()
                : null;
        if (rootMessage != null) {
            return rootMessage;
        }
        String top = exception.getMessage();
        if (top != null && !top.isBlank()) {
            return top.trim();
        }
        return "Data integrity constraint violated";
    }

    static boolean isPostgresRaise(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLException sql
                    && PG_RAISE_EXCEPTION.equals(sql.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    static String sqlRaiseDetail(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLException sql
                    && PG_RAISE_EXCEPTION.equals(sql.getSQLState())
                    && sql.getMessage() != null
                    && !sql.getMessage().isBlank()) {
                return sql.getMessage().trim();
            }
            current = current.getCause();
        }
        String top = exception.getMessage();
        if (top != null && !top.isBlank()) {
            return top.trim();
        }
        return "Database raised an exception";
    }
}
