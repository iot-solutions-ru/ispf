package com.ispf.expression;

import java.util.List;
import java.util.Map;

/**
 * Product-facing CEL formal verification report (Z3 via {@code dev.cel:verifier}).
 *
 * <p>{@link #codes()} are stable machine tokens for UI i18n / AI tools;
 * {@link #findings()} remain human-readable English summaries.
 */
public record FormalVerificationReport(
        String status,
        String engine,
        Boolean satisfiable,
        Boolean alwaysTrue,
        Boolean equivalent,
        List<String> codes,
        List<String> findings,
        Map<String, Object> details
) {
    public static final String STATUS_SKIPPED = "skipped";
    public static final String STATUS_PASSED = "passed";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_INCONCLUSIVE = "inconclusive";
    public static final String STATUS_UNAVAILABLE = "unavailable";

    public static final String CODE_UNSATISFIABLE = "UNSATISFIABLE";
    public static final String CODE_TAUTOLOGY = "TAUTOLOGY";
    public static final String CODE_SATISFIABLE = "SATISFIABLE";
    public static final String CODE_NOT_TAUTOLOGY = "NOT_TAUTOLOGY";
    public static final String CODE_EQUIVALENT = "EQUIVALENT";
    public static final String CODE_NOT_EQUIVALENT = "NOT_EQUIVALENT";
    public static final String CODE_INCONCLUSIVE = "INCONCLUSIVE";
    public static final String CODE_SKIPPED_DISABLED = "SKIPPED_DISABLED";
    public static final String CODE_SKIPPED_EMPTY = "SKIPPED_EMPTY";
    public static final String CODE_SKIPPED_PLATFORM_BINDING = "SKIPPED_PLATFORM_BINDING";
    public static final String CODE_SKIPPED_NON_BOOLEAN = "SKIPPED_NON_BOOLEAN";
    public static final String CODE_SKIPPED_HISTORIAN = "SKIPPED_HISTORIAN_HELPERS";
    public static final String CODE_UNAVAILABLE = "UNAVAILABLE";
    public static final String CODE_PASSED = "PASSED";

    public FormalVerificationReport(
            String status,
            String engine,
            Boolean satisfiable,
            Boolean alwaysTrue,
            List<String> findings,
            Map<String, Object> details
    ) {
        this(status, engine, satisfiable, alwaysTrue, null, List.of(), findings, details);
    }

    public static FormalVerificationReport skipped(String code, String reason) {
        return new FormalVerificationReport(
                STATUS_SKIPPED,
                ExpressionFormalVerifier.ENGINE_ID,
                null,
                null,
                null,
                List.of(code),
                List.of(reason),
                Map.of("reason", reason, "code", code)
        );
    }

    public static FormalVerificationReport unavailable(String reason) {
        return new FormalVerificationReport(
                STATUS_UNAVAILABLE,
                ExpressionFormalVerifier.ENGINE_ID,
                null,
                null,
                null,
                List.of(CODE_UNAVAILABLE),
                List.of(reason),
                Map.of("reason", reason)
        );
    }

    /** True when the expression must not be applied as a boolean condition under hard gate. */
    public boolean blocksConditionApply() {
        return STATUS_FAILED.equals(status);
    }

    public boolean hasCode(String code) {
        return codes != null && codes.contains(code);
    }
}
