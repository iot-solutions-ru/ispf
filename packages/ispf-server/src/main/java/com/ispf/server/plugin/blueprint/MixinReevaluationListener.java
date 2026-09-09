package com.ispf.server.plugin.blueprint;

import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import com.ispf.server.object.bus.ObjectChangeAsyncHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Applies watch MIXINs on object create (ADR-0058).
 */
@Component
public class MixinReevaluationListener implements ObjectChangeAsyncHandler {

    private static final Logger log = LoggerFactory.getLogger(MixinReevaluationListener.class);

    private final MixinReevaluationService mixinReevaluationService;
    private final WatchMixinIndex watchMixinIndex;

    public MixinReevaluationListener(
            MixinReevaluationService mixinReevaluationService,
            WatchMixinIndex watchMixinIndex
    ) {
        this.mixinReevaluationService = mixinReevaluationService;
        this.watchMixinIndex = watchMixinIndex;
    }

    @Override
    public int order() {
        return 25;
    }

    @Override
    public void handle(ObjectChangeEvent event) {
        if (event.type() != ObjectChangeType.CREATED) {
            return;
        }
        if (!event.automationEligible()) {
            return;
        }
        if (watchMixinIndex.watchers(com.ispf.plugin.blueprint.MixinReevaluationTrigger.OBJECT_CREATED).isEmpty()) {
            return;
        }
        try {
            mixinReevaluationService.onObjectCreated(event.path());
        } catch (Exception e) {
            log.warn("Mixin reevaluation on CREATED failed for {}: {}", event.path(), e.getMessage());
        }
    }
}
