package com.ispf.server.object;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Exposes {@link BindingRuleEngine} cascade stop-loss hits as a Micrometer counter.
 */
@Component
public class BindingStopLossMetrics {

    public BindingStopLossMetrics(ObjectProvider<MeterRegistry> meters) {
        MeterRegistry registry = meters.getIfAvailable();
        if (registry == null) {
            return;
        }
        FunctionCounter.builder(
                        "ispf.binding.truncations",
                        BindingRuleEngine.class,
                        ignored -> (double) BindingRuleEngine.truncationTotal()
                )
                .description("Binding rule cascade stop-loss hits (MAX_PASSES / MAX_DEPTH)")
                .register(registry);
    }
}
