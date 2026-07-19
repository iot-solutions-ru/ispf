package com.ispf.server.api;

import com.ispf.expression.BindingExpressionValidator;
import com.ispf.server.expression.ExpressionEvaluationService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/expressions")
public class ExpressionController {

    private final ExpressionEvaluationService evaluationService;

    public ExpressionController(ExpressionEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping("/validate")
    public ValidateResponse validate(@RequestBody ValidateRequest request) {
        try {
            BindingExpressionValidator.validateOrThrow(request.expression());
            return new ValidateResponse(true, request.expression().trim(), null, List.of());
        } catch (Exception e) {
            return new ValidateResponse(false, request.expression(), e.getMessage(), List.of());
        }
    }

    @PostMapping("/evaluate")
    public EvaluateResponse evaluate(@RequestBody EvaluateRequest request) {
        ExpressionEvaluationService.EvaluateResult result = evaluationService.evaluate(
                request.objectPath(),
                request.expression(),
                request.targetVariable(),
                request.breakpoints() != null ? request.breakpoints() : List.of(),
                request.resumeFrom()
        );
        return new EvaluateResponse(
                result.valid(),
                result.expression(),
                result.result(),
                result.resultType(),
                result.error(),
                result.steps().stream()
                        .map(step -> new EvaluateStepResponse(step.phase(), step.status(), step.detail()))
                        .toList(),
                result.paused(),
                result.pausedAt()
        );
    }

    public record ValidateRequest(@NotBlank String expression) {
    }

    public record ValidateResponse(boolean valid, String expression, String error, List<String> warnings) {
    }

    public record EvaluateRequest(
            @NotBlank String objectPath,
            @NotBlank String expression,
            String targetVariable,
            List<String> breakpoints,
            String resumeFrom
    ) {
    }

    public record EvaluateStepResponse(String phase, String status, Object detail) {
    }

    public record EvaluateResponse(
            boolean valid,
            String expression,
            Object result,
            String resultType,
            String error,
            java.util.List<EvaluateStepResponse> steps,
            boolean paused,
            String pausedAt
    ) {
    }
}
