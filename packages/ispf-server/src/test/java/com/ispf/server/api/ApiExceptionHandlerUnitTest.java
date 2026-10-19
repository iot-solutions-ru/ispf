package com.ispf.server.api;

import com.ispf.expression.ExpressionException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerUnitTest {

    @Test
    void expressionExceptionReturnsBadRequestProblemDetail() {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        ProblemDetail detail = handler.handleExpression(
                new ExpressionException("Binding expression failed: no_such_name: Invalid expression: no_such_name")
        );
        assertThat(detail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(String.valueOf(detail.getDetail())).contains("no_such_name");
        assertThat(detail.getTitle()).isEqualTo("Expression error");
    }

    @Test
    void dataIntegrityViolationReturnsConflictProblemDetail() {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        DuplicateKeyException ex = new DuplicateKeyException(
                "PreparedStatementCallback",
                new RuntimeException(
                        "ERROR: duplicate key value violates unique constraint \"uq_shipment_order_number\""
                )
        );
        ProblemDetail detail = handler.handleDataIntegrity(ex);
        assertThat(detail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(detail.getTitle()).isEqualTo("Data integrity violation");
        assertThat(String.valueOf(detail.getDetail())).contains("uq_shipment_order_number");
    }
}
