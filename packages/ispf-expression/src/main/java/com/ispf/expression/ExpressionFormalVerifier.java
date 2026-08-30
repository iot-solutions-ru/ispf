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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Product CEL formal verification (satisfiability, tautology, equivalence).
 *
 * <p>Thread-safe. Verifier instances are cached by timeout. Callers that need
 * platform policy (enable/enforce) should use the Spring
 * {@code ExpressionFormalVerificationService} wrapper.
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

    private static final ConcurrentHashMap<Long, CelVerifier> VERIFIERS = new ConcurrentHashMap<>();

    private ExpressionFormalVerifier() {
    }

    /** Tunables for a verification pass. */
    public record Options(
            boolean enabled,
            Duration timeout,
            boolean rejectUnsatisfiable,
            boolean rejectTautology
    ) {
        public static Options defaults() {
            return new Options(true, DEFAULT_TIMEOUT, true, true);
        }

        public Options {
            timeout = timeout == null || timeout.isNegative() || timeout.isZero() ? DEFAULT_TIMEOUT : timeout;
        }
    }

    public static FormalVerificationReport analyze(String expression) {
        return analyze(expression, Options.defaults());
    }

    public static FormalVerificationReport analyze(String expression, Options options) {
        Options opts = options != null ? options : Options.defaults();
        if (!opts.enabled()) {
            return FormalVerificationReport.skipped(
                    FormalVerificationReport.CODE_SKIPPED_DISABLED,
                    "formal verification disabled"
            );
        }
        if (expression == null || expression.isBlank()) {
            return FormalVerificationReport.skipped(
                    FormalVerificationReport.CODE_SKIPPED_EMPTY,
                    "empty expression"
            );
        }
        String trimmed = expression.trim();
        if (PlatformBindingRegistry.matches(trimmed)) {
            return FormalVerificationReport.skipped(
                    FormalVerificationReport.CODE_SKIPPED_PLATFORM_BINDING,
                    "platform binding helper (non-CEL)"
            );
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
                    FormalVerificationReport.CODE_SKIPPED_NON_BOOLEAN,
                    "non-boolean result type (" + ast.getResultType().name() + "); formal checks apply to conditions"
            );
        }

        return analyzeBooleanAst(ast, opts);
    }

    public static FormalVerificationReport verifyEquivalence(String left, String right) {
        return verifyEquivalence(left, right, Options.defaults());
    }

    public static FormalVerificationReport verifyEquivalence(String left, String right, Options options) {
        Options opts = options != null ? options : Options.defaults();
        if (!opts.enabled()) {
            return FormalVerificationReport.skipped(
                    FormalVerificationReport.CODE_SKIPPED_DISABLED,
                    "formal verification disabled"
            );
        }
        if (left == null || left.isBlank() || right == null || right.isBlank()) {
            return FormalVerificationReport.skipped(
                    FormalVerificationReport.CODE_SKIPPED_EMPTY,
                    "both expressions are required for equivalence"
            );
        }
        try {
            CelAbstractSyntaxTree astA = COMPILER.compile(ExpressionEngine.normalizeMapIndexSelects(left.trim())).getAst();
            CelAbstractSyntaxTree astB = COMPILER.compile(ExpressionEngine.normalizeMapIndexSelects(right.trim())).getAst();
            CelVerificationResult result = verifier(opts.timeout()).verifyEquivalence(astA, astB);
            List<String> findings = new ArrayList<>();
            List<String> codes = new ArrayList<>();
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("equivalenceStatus", result.status().name());
            details.put("equivalenceMessage", nullToEmpty(result.message()));
            if (result.counterexample() != null && !result.counterexample().isBlank()) {
                details.put("counterexample", result.counterexample());
            }
            if (result.status() == VerificationStatus.VERIFIED) {
                codes.add(FormalVerificationReport.CODE_EQUIVALENT);
                findings.add("expressions are logically equivalent");
                return new FormalVerificationReport(
                        FormalVerificationReport.STATUS_PASSED,
                        ENGINE_ID,
                        null,
                        null,
                        true,
                        List.copyOf(codes),
                        List.copyOf(findings),
                        Map.copyOf(details)
                );
            }
            if (result.status() == VerificationStatus.VIOLATED) {
                codes.add(FormalVerificationReport.CODE_NOT_EQUIVALENT);
                findings.add("expressions are not equivalent: " + nullToEmpty(result.message()));
                return new FormalVerificationReport(
                        FormalVerificationReport.STATUS_FAILED,
                        ENGINE_ID,
                        null,
                        null,
                        false,
                        List.copyOf(codes),
                        List.copyOf(findings),
                        Map.copyOf(details)
                );
            }
            codes.add(FormalVerificationReport.CODE_INCONCLUSIVE);
            findings.add("equivalence inconclusive: " + nullToEmpty(result.message()));
            return new FormalVerificationReport(
                    FormalVerificationReport.STATUS_INCONCLUSIVE,
                    ENGINE_ID,
                    null,
                    null,
                    null,
                    List.copyOf(codes),
                    List.copyOf(findings),
                    Map.copyOf(details)
            );
        } catch (CelValidationException ex) {
            return FormalVerificationReport.unavailable("CEL compile failed before equivalence: " + ex.getMessage());
        } catch (CelVerificationException ex) {
            return FormalVerificationReport.unavailable("verifier timeout/error: " + ex.getMessage());
        } catch (RuntimeException ex) {
            return FormalVerificationReport.unavailable("verifier error: " + ex.getMessage());
        }
    }

    /**
     * Compile + formal-verify a boolean condition. Throws when compile fails or
     * formal verification blocks apply (unsatisfiable / tautology per options).
     */
    public static FormalVerificationReport requireSafeConditionOrThrow(String expression) {
        return requireSafeConditionOrThrow(expression, Options.defaults());
    }

    public static FormalVerificationReport requireSafeConditionOrThrow(String expression, Options options) {
        if (expression == null || expression.isBlank()) {
            return FormalVerificationReport.skipped(
                    FormalVerificationReport.CODE_SKIPPED_EMPTY,
                    "empty expression"
            );
        }
        BindingExpressionValidator.validateOrThrow(expression);
        FormalVerificationReport report = analyze(expression, options);
        if (report.blocksConditionApply()) {
            throw new ExpressionException(
                    "Formal verification rejected condition: " + String.join("; ", report.findings())
            );
        }
        return report;
    }

    private static FormalVerificationReport analyzeBooleanAst(CelAbstractSyntaxTree ast, Options opts) {
        List<String> findings = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        Map<String, Object> details = new LinkedHashMap<>();
        Boolean satisfiable = null;
        Boolean alwaysTrue = null;
        String status = FormalVerificationReport.STATUS_PASSED;

        try {
            CelVerifier verifier = verifier(opts.timeout());
            CelVerificationResult sat = verifier.isSatisfiable(ast);
            details.put("satisfiableStatus", sat.status().name());
            details.put("satisfiableMessage", nullToEmpty(sat.message()));
            if (sat.status() == VerificationStatus.VERIFIED) {
                satisfiable = true;
                codes.add(FormalVerificationReport.CODE_SATISFIABLE);
                if (sat.message() != null && !sat.message().isBlank()) {
                    findings.add("satisfiable witness: " + sat.message().trim());
                    details.put("witness", sat.message().trim());
                }
            } else if (sat.status() == VerificationStatus.VIOLATED) {
                satisfiable = false;
                codes.add(FormalVerificationReport.CODE_UNSATISFIABLE);
                findings.add("unsatisfiable: expression can never be true (dead condition)");
                if (opts.rejectUnsatisfiable()) {
                    status = FormalVerificationReport.STATUS_FAILED;
                }
            } else {
                codes.add(FormalVerificationReport.CODE_INCONCLUSIVE);
                findings.add("satisfiability inconclusive: " + nullToEmpty(sat.message()));
                status = FormalVerificationReport.STATUS_INCONCLUSIVE;
            }

            CelVerificationResult tautology = verifier.isAlwaysTrue(ast);
            details.put("alwaysTrueStatus", tautology.status().name());
            details.put("alwaysTrueMessage", nullToEmpty(tautology.message()));
            if (tautology.status() == VerificationStatus.VERIFIED) {
                alwaysTrue = true;
                codes.add(FormalVerificationReport.CODE_TAUTOLOGY);
                findings.add("tautology: expression is always true (condition never discriminates)");
                if (tautology.counterexample() != null && !tautology.counterexample().isBlank()) {
                    details.put("alwaysTrueDetail", tautology.counterexample());
                }
                if (opts.rejectTautology()) {
                    status = FormalVerificationReport.STATUS_FAILED;
                }
            } else if (tautology.status() == VerificationStatus.VIOLATED) {
                alwaysTrue = false;
                codes.add(FormalVerificationReport.CODE_NOT_TAUTOLOGY);
            } else if (!FormalVerificationReport.STATUS_FAILED.equals(status)) {
                codes.add(FormalVerificationReport.CODE_INCONCLUSIVE);
                findings.add("always-true check inconclusive: " + nullToEmpty(tautology.message()));
                status = FormalVerificationReport.STATUS_INCONCLUSIVE;
            }
        } catch (CelVerificationException ex) {
            return FormalVerificationReport.unavailable("verifier timeout/error: " + ex.getMessage());
        } catch (RuntimeException ex) {
            return FormalVerificationReport.unavailable("verifier error: " + ex.getMessage());
        }

        if (findings.isEmpty() && FormalVerificationReport.STATUS_PASSED.equals(status)) {
            codes.add(FormalVerificationReport.CODE_PASSED);
            findings.add("boolean condition is satisfiable and not a tautology");
        }
        return new FormalVerificationReport(
                status,
                ENGINE_ID,
                satisfiable,
                alwaysTrue,
                null,
                List.copyOf(codes),
                List.copyOf(findings),
                Map.copyOf(details)
        );
    }

    private static CelVerifier verifier(Duration timeout) {
        long millis = timeout.toMillis();
        return VERIFIERS.computeIfAbsent(millis, ignored ->
                CelVerifierFactory.newVerifier().setTimeout(timeout).build()
        );
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
