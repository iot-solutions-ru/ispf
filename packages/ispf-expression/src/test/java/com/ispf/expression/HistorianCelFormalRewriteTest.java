package com.ispf.expression;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HistorianCelFormalRewriteTest {

    @Test
    void correlatesIdenticalHelperCalls() {
        HistorianCelFormalRewrite.Result result = HistorianCelFormalRewrite.rewrite(
                "avg(root.a/temp, 5m) > 80.0 && avg(root.a/temp, 5m) < 50.0"
        );
        assertThat(result.rewrittenAny()).isTrue();
        assertThat(result.rewritten()).isEqualTo("self.__hist0 > 80.0 && self.__hist0 < 50.0");
        FormalVerificationReport report = ExpressionFormalVerifier.analyze(result.rewritten());
        assertThat(report.blocksConditionApply()).isTrue();
        assertThat(report.codes()).contains(FormalVerificationReport.CODE_UNSATISFIABLE);
    }

    @Test
    void distinctCallsGetDistinctPlaceholders() {
        HistorianCelFormalRewrite.Result result = HistorianCelFormalRewrite.rewrite(
                "avg(root.a/temp, 5m) > live(root.a/temp)"
        );
        assertThat(result.rewritten()).isEqualTo("self.__hist0 > self.__hist1");
        assertThat(result.placeholders()).hasSize(2);
    }
}
