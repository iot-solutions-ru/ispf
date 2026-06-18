package com.ispf.server.api;

import com.ispf.expression.ExpressionEngine;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/expressions")
public class ExpressionController {

    private final ExpressionEngine engine = new ExpressionEngine();

    @PostMapping("/validate")
    public ValidateResponse validate(@RequestBody ValidateRequest request) {
        try {
            var compiled = engine.compile(request.expression());
            return new ValidateResponse(true, compiled.source(), null);
        } catch (Exception e) {
            return new ValidateResponse(false, request.expression(), e.getMessage());
        }
    }

    public record ValidateRequest(@NotBlank String expression) {
    }

    public record ValidateResponse(boolean valid, String expression, String error) {
    }
}
