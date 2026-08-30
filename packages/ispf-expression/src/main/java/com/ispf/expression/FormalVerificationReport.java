package com.ispf.expression;

import java.util.List;
import java.util.Map;

/**
 * Result of CEL formal verification (Z3-backed {@code dev.cel:verifier}).
 *
 * <p>Used as an AI safety gate: catch unsatisfiable / always-true boolean
 * conditions before bindings, alerts, and workflow rules are applied.
 */
public record FormalVerificationReport(
        String status,
        String engine,
        Boolean satisfiable,
        Boolean alwaysTrue,
        List<String> findings,
        Map<String, Object> details
) {
    public static final String STATUS_SKIPPED = "skipped";
    public static final String STATUS_PASSED = "passed";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_INCONCLUSIVE = "inconclusive";
    public static final String STATUS_UNAVAILABLE = "unavailable";

    public static FormalVerificationReport skipped(String reason) {
        return new FormalVerificationReport(
                STATUS_SKIPPED,
                ExpressionFormalVerifier.ENGINE_ID,
                null,
                null,
                List.of(reason),
                Map.of("reason", reason)
        );
    }

    public static FormalVerificationReport unavailable(String reason) {
        return new FormalVerificationReport(
                STATUS_UNAVAILABLE,
                ExpressionFormalVerifier.ENGINE_ID,
                null,
                null,
                List.of(reason),
                Map.of("reason", reason)
        );
    }

    /** True when the expression must not be applied as a boolean condition. */
    public boolean blocksConditionApply() {
        return STATUS_FAILED.equals(status);
    }
}
