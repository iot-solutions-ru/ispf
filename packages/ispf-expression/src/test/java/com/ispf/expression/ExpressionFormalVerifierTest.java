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
        assertThat(report.findings().getFirst()).containsIgnoringCase("unsatisfiable");
    }

    @Test
    void rejectsTautology() {
        FormalVerificationReport report = ExpressionFormalVerifier.analyze("true || self.temp > 0.0");
        assertThat(report.status()).isEqualTo(FormalVerificationReport.STATUS_FAILED);
        assertThat(report.alwaysTrue()).isTrue();
        assertThat(report.blocksConditionApply()).isTrue();
    }

    @Test
    void acceptsDiscriminatingCondition() {
        FormalVerificationReport report = ExpressionFormalVerifier.analyze("self.temp > 85.0");
        assertThat(report.status()).isEqualTo(FormalVerificationReport.STATUS_PASSED);
        assertThat(report.satisfiable()).isTrue();
        assertThat(report.alwaysTrue()).isFalse();
        assertThat(report.blocksConditionApply()).isFalse();
    }

    @Test
    void skipsNonBooleanExpression() {
        FormalVerificationReport report = ExpressionFormalVerifier.analyze("self.temp * 1.8 + 32.0");
        assertThat(report.status()).isEqualTo(FormalVerificationReport.STATUS_SKIPPED);
        assertThat(report.findings().getFirst()).containsIgnoringCase("non-boolean");
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
