package com.ispf.analytics.coreext;

import com.ispf.analytics.engine.AnalyticsEvaluator;
import com.ispf.analytics.engine.eval.EnergyDeltaEvaluator;
import com.ispf.analytics.spi.AnalyticsFunctionDescriptor;
import com.ispf.analytics.spi.AnalyticsFunctionParameterDescriptor;
import com.ispf.analytics.spi.AnalyticsFunctionProvider;

import java.util.List;
import java.util.Set;

public final class EnergyDeltaFunctionProvider implements AnalyticsFunctionProvider {

    @Override
    public AnalyticsFunctionDescriptor getDescriptor() {
        return new AnalyticsFunctionDescriptor(
                "energyDelta",
                "Energy delta",
                "energyDelta",
                "energyDelta(sourcePath, window)",
                List.of(
                        new AnalyticsFunctionParameterDescriptor("sourcePath", "tagPath", true, "Input historian source"),
                        new AnalyticsFunctionParameterDescriptor("window", "duration", false, "Window bucket, default 5m")
                ),
                Set.of("energy", "historian", "delta"),
                "core-ext"
        );
    }

    @Override
    public AnalyticsEvaluator createEvaluator() {
        return new EnergyDeltaEvaluator();
    }
}
