package com.ispf.server.config;

import com.ispf.server.relational.RelationalDialect;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * ADR-0037 / 0078: MySQL and Oracle metadata packs are not certified
 * (also enforced pre-Flyway via {@link MetadataDialectBootPolicy}).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 19)
public class UncertifiedRelationalEngineBootGuard {

    private final RelationalDialect relationalDialect;

    public UncertifiedRelationalEngineBootGuard(RelationalDialect relationalDialect) {
        this.relationalDialect = relationalDialect;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void refuseUncertifiedEngines() {
        MetadataDialectBootPolicy.assertEngineCertified(relationalDialect);
    }
}
