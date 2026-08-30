package com.ispf.server.api;

import com.ispf.expression.BindingExpressionValidator;
import com.ispf.expression.ExpressionException;
import com.ispf.expression.FormalVerificationReport;
import com.ispf.server.expression.ExpressionEvaluationService;
import com.ispf.server.expression.ExpressionFormalVerificationService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/expressions")
public class ExpressionController {

    private final ExpressionEvaluationService evaluationService;
    private final ExpressionFormalVerificationService formalVerificationService;

    public ExpressionController(
            ExpressionEvaluationService evaluationService,
            ExpressionFormalVerificationService formalVerificationService
    ) {
        this.evaluationService = evaluationService;
        this.formalVerificationService = formalVerificationService;
    }

    @PostMapping("/validate")
    public ValidateResponse validate(@RequestBody ValidateRequest request) {
        try {
            BindingExpressionValidator.validateOrThrow(request.expression());
            FormalVerificationReport verification = formalVerificationService.analyze(request.expression());
            List<String> warnings = new ArrayList<>(verification.findings());
            if (formalVerificationService.shouldBlockOnValidate(verification)) {
                return new ValidateResponse(
                        false,
                        request.expression().trim(),
                        "Formal verification rejected condition: " + String.join("; ", verification.findings()),
                        warnings,
                        verification
                );
            }
            return new ValidateResponse(true, request.expression().trim(), null, warnings, verification);
        } catch (ExpressionException e) {
            return new ValidateResponse(false, request.expression(), e.getMessage(), List.of(), null);
        } catch (Exception e) {
            return new ValidateResponse(false, request.expression(), e.getMessage(), List.of(), null);
        }
    }

    /** Dedicated formal verification endpoint (product API for UI / AI / CI). */
    @PostMapping("/verify")
    public VerifyResponse verify(@RequestBody VerifyRequest request) {
        FormalVerificationReport report = formalVerificationService.analyze(request.expression());
        boolean blocked = formalVerificationService.shouldBlockOnValidate(report);
        return new VerifyResponse(!blocked || !report.blocksConditionApply(), report);
    }

    /** Prove two CEL expressions are logically equivalent (AI refactor safety). */
    @PostMapping("/verify-equivalence")
    public VerifyResponse verifyEquivalence(@RequestBody EquivalenceRequest request) {
        FormalVerificationReport report = formalVerificationService.verifyEquivalence(
                request.left(),
                request.right()
        );
        boolean ok = !report.blocksConditionApply();
        return new VerifyResponse(ok, report);
    }

    @PostMapping("/evaluate")
    public EvaluateResponse evaluate(@RequestBody EvaluateRequest request, Authentication authentication) {
        ExpressionEvaluationService.EvaluateResult result = evaluationService.evaluate(
                request.objectPath(),
                request.expression(),
                request.targetVariable(),
                request.breakpoints() != null ? request.breakpoints() : List.of(),
                request.resumeFrom(),
                authentication
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

    public record ValidateResponse(
            boolean valid,
            String expression,
            String error,
            List<String> warnings,
            FormalVerificationReport verification
    ) {
        public ValidateResponse(boolean valid, String expression, String error, List<String> warnings) {
            this(valid, expression, error, warnings, null);
        }
    }

    public record VerifyRequest(@NotBlank String expression) {
    }

    public record EquivalenceRequest(@NotBlank String left, @NotBlank String right) {
    }

    public record VerifyResponse(boolean ok, FormalVerificationReport verification) {
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
