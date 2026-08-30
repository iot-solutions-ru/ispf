package com.ispf.expression;

import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelOptions;
import dev.cel.common.CelValidationException;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.verifier.CelVerificationException;
import dev.cel.verifier.CelVerificationResult;
import dev.cel.verifier.CelVerificationResult.VerificationStatus;
import dev.cel.verifier.CelVerifier;
import dev.cel.verifier.CelVerifierFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Formal verification gate for ISPF CEL expressions (AI-first safety net).
 *
 * <p>Boolean conditions are checked for satisfiability and tautology via
 * {@code dev.cel:verifier} (Z3). Non-boolean expressions and platform binding
 * helpers are skipped — compile validation remains the caller's responsibility.
 */
public final class ExpressionFormalVerifier {

    public static final String ENGINE_ID = "cel-verifier-0.14";

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);

    private static final CelOptions CEL_OPTIONS = CelOptions.current()
            .enableHeterogeneousNumericComparisons(true)
            .build();

    private static final CelCompiler COMPILER = CelCompilerFactory.standardCelCompilerBuilder()
            .setOptions(CEL_OPTIONS)
            .addVar("self", SimpleType.DYN)
            .addVar("parent", SimpleType.DYN)
            .addVar("context", SimpleType.DYN)
            .addVar("input", SimpleType.DYN)
            .build();

    private static final CelVerifier VERIFIER = CelVerifierFactory.newVerifier()
            .setTimeout(DEFAULT_TIMEOUT)
            .build();

    private ExpressionFormalVerifier() {
    }

    /**
     * Analyze a CEL expression for boolean condition safety.
     *
     * @param expression raw CEL (already normalized map-index selects if desired)
     */
    public static FormalVerificationReport analyze(String expression) {
        if (expression == null || expression.isBlank()) {
            return FormalVerificationReport.skipped("empty expression");
        }
        String trimmed = expression.trim();
        if (PlatformBindingRegistry.matches(trimmed)) {
            return FormalVerificationReport.skipped("platform binding helper (non-CEL)");
        }

        CelAbstractSyntaxTree ast;
        try {
            String normalized = ExpressionEngine.normalizeMapIndexSelects(trimmed);
            ast = COMPILER.compile(normalized).getAst();
        } catch (CelValidationException | RuntimeException ex) {
            return FormalVerificationReport.unavailable("CEL compile failed before formal verify: " + ex.getMessage());
        }

        if (!SimpleType.BOOL.equals(ast.getResultType())) {
            return FormalVerificationReport.skipped(
                    "non-boolean result type (" + ast.getResultType().name() + "); formal checks apply to conditions"
            );
        }

        List<String> findings = new ArrayList<>();
        Map<String, Object> details = new LinkedHashMap<>();
        Boolean satisfiable = null;
        Boolean alwaysTrue = null;
        String status = FormalVerificationReport.STATUS_PASSED;

        try {
            CelVerificationResult sat = VERIFIER.isSatisfiable(ast);
            details.put("satisfiableStatus", sat.status().name());
            details.put("satisfiableMessage", nullToEmpty(sat.message()));
            if (sat.status() == VerificationStatus.VERIFIED) {
                satisfiable = true;
                if (sat.message() != null && !sat.message().isBlank()) {
                    findings.add("satisfiable witness: " + sat.message().trim());
                }
            } else if (sat.status() == VerificationStatus.VIOLATED) {
                satisfiable = false;
                status = FormalVerificationReport.STATUS_FAILED;
                findings.add("unsatisfiable: expression can never be true (dead condition)");
            } else {
                status = FormalVerificationReport.STATUS_INCONCLUSIVE;
                findings.add("satisfiability inconclusive: " + nullToEmpty(sat.message()));
            }

            CelVerificationResult tautology = VERIFIER.isAlwaysTrue(ast);
            details.put("alwaysTrueStatus", tautology.status().name());
            details.put("alwaysTrueMessage", nullToEmpty(tautology.message()));
            if (tautology.status() == VerificationStatus.VERIFIED) {
                alwaysTrue = true;
                status = FormalVerificationReport.STATUS_FAILED;
                findings.add("tautology: expression is always true (condition never discriminates)");
                if (tautology.counterexample() != null && !tautology.counterexample().isBlank()) {
                    details.put("alwaysTrueDetail", tautology.counterexample());
                }
            } else if (tautology.status() == VerificationStatus.VIOLATED) {
                alwaysTrue = false;
            } else if (!FormalVerificationReport.STATUS_FAILED.equals(status)) {
                status = FormalVerificationReport.STATUS_INCONCLUSIVE;
                findings.add("always-true check inconclusive: " + nullToEmpty(tautology.message()));
            }
        } catch (CelVerificationException ex) {
            return FormalVerificationReport.unavailable("verifier timeout/error: " + ex.getMessage());
        } catch (RuntimeException ex) {
            return FormalVerificationReport.unavailable("verifier error: " + ex.getMessage());
        }

        if (findings.isEmpty() && FormalVerificationReport.STATUS_PASSED.equals(status)) {
            findings.add("boolean condition is satisfiable and not a tautology");
        }
        return new FormalVerificationReport(status, ENGINE_ID, satisfiable, alwaysTrue, List.copyOf(findings), Map.copyOf(details));
    }

    /**
     * Compile + formal-verify a boolean condition. Throws when compile fails or
     * formal verification blocks apply (unsatisfiable / tautology).
     */
    public static FormalVerificationReport requireSafeConditionOrThrow(String expression) {
        if (expression == null || expression.isBlank()) {
            return FormalVerificationReport.skipped("empty expression");
        }
        BindingExpressionValidator.validateOrThrow(expression);
        FormalVerificationReport report = analyze(expression);
        if (report.blocksConditionApply()) {
            throw new ExpressionException(
                    "Formal verification rejected condition: " + String.join("; ", report.findings()),
                    null
            );
        }
        return report;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
