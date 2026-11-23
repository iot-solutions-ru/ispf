package com.ispf.server.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerFieldValidationTest {

    @Test
    void fieldValidationNamesTheRejectedField() throws Exception {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "createObjectRequest");
        binding.addError(new FieldError("createObjectRequest", "name", "must not be blank"));
        binding.addError(new FieldError("createObjectRequest", "paths", "must not be empty"));
        MethodParameter parameter = new MethodParameter(
                ApiExceptionHandlerFieldValidationTest.class.getDeclaredMethod("sample", String.class),
                0
        );

        ProblemDetail detail = new ApiExceptionHandler().handleMethodArgumentNotValid(
                new MethodArgumentNotValidException(parameter, binding)
        );

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(detail.getTitle()).isEqualTo("Invalid request");
        assertThat(detail.getDetail()).contains("name: must not be blank");
        assertThat(detail.getDetail()).contains("paths: must not be empty");
    }

    @Test
    void illegalArgumentKeepsItsOwnMessage() {
        ProblemDetail detail = new ApiExceptionHandler().handleIllegalArgument(
                new IllegalArgumentException("diagramJson is required")
        );

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(detail.getTitle()).isEqualTo("Invalid request");
        assertThat(detail.getDetail()).isEqualTo("diagramJson is required");
    }

    @SuppressWarnings("unused")
    private void sample(String body) {
    }
}
