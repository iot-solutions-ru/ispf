package com.ispf.expression;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpressionFormalVerifierTest {

    @Test
    void rejectsUnsatisfiableCondition() {
        FormalVerificationReport report = ExpressionFormalVerifier.analyze(
                "self.temp > 100.0 && self.temp < 50.0"
        );
        assertThat(report.status()).isEqualTo(FormalVerificationReport.STATUS_FAILED);
        assertThat(report.satisfiable()).isFalse();
        assertThat(report.blocksConditionApply()).isTrue();
        assertThat(report.codes()).contains(FormalVerificationReport.CODE_UNSATISFIABLE);
        assertThat(report.findings().getFirst()).containsIgnoringCase("unsatisfiable");
    }

    @Test
    void rejectsTautology() {
        FormalVerificationReport report = ExpressionFormalVerifier.analyze("true || self.temp > 0.0");
        assertThat(report.status()).isEqualTo(FormalVerificationReport.STATUS_FAILED);
        assertThat(report.alwaysTrue()).isTrue();
        assertThat(report.blocksConditionApply()).isTrue();
        assertThat(report.codes()).contains(FormalVerificationReport.CODE_TAUTOLOGY);
    }

    @Test
    void acceptsDiscriminatingCondition() {
        FormalVerificationReport report = ExpressionFormalVerifier.analyze("self.temp > 85.0");
        assertThat(report.status()).isEqualTo(FormalVerificationReport.STATUS_PASSED);
        assertThat(report.satisfiable()).isTrue();
        assertThat(report.alwaysTrue()).isFalse();
        assertThat(report.blocksConditionApply()).isFalse();
        assertThat(report.codes()).contains(FormalVerificationReport.CODE_SATISFIABLE);
    }

    @Test
    void skipsNonBooleanExpression() {
        FormalVerificationReport report = ExpressionFormalVerifier.analyze("self.temp * 1.8 + 32.0");
        assertThat(report.status()).isEqualTo(FormalVerificationReport.STATUS_SKIPPED);
        assertThat(report.codes()).contains(FormalVerificationReport.CODE_SKIPPED_NON_BOOLEAN);
    }

    @Test
    void provesEquivalence() {
        FormalVerificationReport report = ExpressionFormalVerifier.verifyEquivalence(
                "self.x > 10.0",
                "10.0 < self.x"
        );
        assertThat(report.status()).isEqualTo(FormalVerificationReport.STATUS_PASSED);
        assertThat(report.equivalent()).isTrue();
        assertThat(report.codes()).contains(FormalVerificationReport.CODE_EQUIVALENT);
    }

    @Test
    void detectsNonEquivalence() {
        FormalVerificationReport report = ExpressionFormalVerifier.verifyEquivalence(
                "self.x > 10.0",
                "self.x > 20.0"
        );
        assertThat(report.status()).isEqualTo(FormalVerificationReport.STATUS_FAILED);
        assertThat(report.equivalent()).isFalse();
        assertThat(report.codes()).contains(FormalVerificationReport.CODE_NOT_EQUIVALENT);
    }

    @Test
    void disabledOptionsSkip() {
        FormalVerificationReport report = ExpressionFormalVerifier.analyze(
                "self.temp > 100.0 && self.temp < 50.0",
                new ExpressionFormalVerifier.Options(false, java.time.Duration.ofSeconds(1), true, true)
        );
        assertThat(report.status()).isEqualTo(FormalVerificationReport.STATUS_SKIPPED);
        assertThat(report.codes()).contains(FormalVerificationReport.CODE_SKIPPED_DISABLED);
    }

    @Test
    void requireSafeConditionOrThrowBlocksDeadCondition() {
        assertThatThrownBy(() ->
                ExpressionFormalVerifier.requireSafeConditionOrThrow("self.x > 10.0 && self.x < 0.0")
        )
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("Formal verification rejected");
    }
}
