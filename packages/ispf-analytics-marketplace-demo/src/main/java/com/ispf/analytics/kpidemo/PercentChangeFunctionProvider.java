package com.ispf.analytics.kpidemo;

import com.ispf.analytics.engine.AnalyticsEvaluator;
import com.ispf.analytics.engine.eval.PercentChangeEvaluator;
import com.ispf.analytics.spi.AnalyticsFunctionDescriptor;
import com.ispf.analytics.spi.AnalyticsFunctionParameterDescriptor;
import com.ispf.analytics.spi.AnalyticsFunctionProvider;

import java.util.List;
import java.util.Set;

public final class PercentChangeFunctionProvider implements AnalyticsFunctionProvider {

    @Override
    public AnalyticsFunctionDescriptor getDescriptor() {
        return new AnalyticsFunctionDescriptor(
                "percentChange",
                "Percent change in window",
                "percentChange",
                "percentChange(sourcePath, window)",
                List.of(
                        new AnalyticsFunctionParameterDescriptor(
                                "sourcePath",
                                "tagPath",
                                true,
                                "Historian source path.variable"
                        ),
                        new AnalyticsFunctionParameterDescriptor(
                                "window",
                                "duration",
                                false,
                                "Window bucket, default from rule windowBucket"
                        )
                ),
                Set.of("kpi", "historian", "statistics", "percent"),
                "ispf-analytics-kpi-demo"
        );
    }

    @Override
    public AnalyticsEvaluator createEvaluator() {
        return new PercentChangeEvaluator();
    }
}
