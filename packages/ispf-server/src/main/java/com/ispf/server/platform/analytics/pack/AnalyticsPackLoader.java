package com.ispf.server.platform.analytics.pack;

import com.ispf.analytics.engine.AnalyticsEvaluator;
import com.ispf.analytics.spi.AnalyticsFunctionDescriptor;
import com.ispf.analytics.spi.AnalyticsFunctionProvider;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ServiceLoader;

@Component
public class AnalyticsPackLoader {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsPackLoader.class);

    private final AnalyticsExtensionRegistry extensionRegistry;

    public AnalyticsPackLoader(AnalyticsExtensionRegistry extensionRegistry) {
        this.extensionRegistry = extensionRegistry;
    }

    @PostConstruct
    public void loadOnStartup() {
        ServiceLoader<AnalyticsFunctionProvider> providers = ServiceLoader.load(AnalyticsFunctionProvider.class);
        for (AnalyticsFunctionProvider provider : providers) {
            register(provider);
        }
    }

    void register(AnalyticsFunctionProvider provider) {
        AnalyticsFunctionDescriptor descriptor = provider.getDescriptor();
        if (descriptor == null) {
            log.warn("Skipping analytics provider {}: descriptor is null", provider.getClass().getName());
            return;
        }
        String helperId = safe(descriptor.helper());
        if (helperId.isBlank()) {
            log.warn("Skipping analytics provider {}: helper is blank", provider.getClass().getName());
            return;
        }
        if (extensionRegistry.containsHelper(helperId)) {
            log.warn("Skipping duplicate analytics helper {} from {}", helperId, provider.getClass().getName());
            return;
        }

        AnalyticsEvaluator evaluator = provider.createEvaluator();
        if (evaluator == null) {
            log.warn("Skipping analytics provider {}: evaluator is null", provider.getClass().getName());
            return;
        }
        if (!helperId.equals(evaluator.helper())) {
            log.warn(
                    "Skipping analytics provider {}: helper mismatch descriptor={} evaluator={}",
                    provider.getClass().getName(),
                    helperId,
                    evaluator.helper()
            );
            return;
        }

        String packId = safe(descriptor.packId());
        if (packId.isBlank()) {
            packId = provider.getClass().getPackageName();
        }

        extensionRegistry.register(new AnalyticsExtensionRegistry.RegisteredAnalyticsFunction(
                packId,
                helperId,
                descriptor,
                evaluator
        ));
        log.info("Analytics extension function loaded: helper={} pack={}", helperId, packId);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
