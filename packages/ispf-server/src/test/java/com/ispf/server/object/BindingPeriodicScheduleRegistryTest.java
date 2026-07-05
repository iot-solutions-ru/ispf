package com.ispf.server.object;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class BindingPeriodicScheduleRegistryTest {

    private static final String DEVICE = "root.platform.devices.demo-sensor-01";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BindingPeriodicScheduleRegistry registry;

    @Autowired
    private BindingRuleEngine bindingRuleEngine;

    @BeforeEach
    void cleanTable() {
        registry.clearAll();
    }

    @Test
    void syncObjectInsertsPeriodicRule() {
        BindingRule rule = periodicRule("rule-a", 500);

        registry.syncObject(DEVICE, List.of(rule));

        assertThat(registry.countEnabled()).isEqualTo(1);
        assertThat(registry.nextWakeAt()).isNotNull();
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM platform_binding_periodic_rules WHERE object_path = ? AND rule_id = ?",
                Integer.class,
                DEVICE,
                "rule-a"
        );
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void syncObjectRemovesDisabledOrNonPeriodicRules() {
        registry.syncObject(DEVICE, List.of(periodicRule("rule-a", 500)));
        registry.syncObject(DEVICE, List.of());

        assertThat(registry.countEnabled()).isZero();
    }

    @Test
    void removeSubtreeDeletesNestedPaths() {
        registry.syncObject(DEVICE, List.of(periodicRule("rule-a", 500)));
        registry.syncObject(DEVICE + ".child", List.of(periodicRule("rule-b", 1000)));

        registry.removeSubtree(DEVICE);

        assertThat(registry.countEnabled()).isZero();
    }

    @Test
    void fireDueAdvancesNextRunAt() {
        registry.syncObject(DEVICE, List.of(periodicRule("rule-a", 100)));
        Instant before = registry.nextWakeAt();
        assertThat(before).isNotNull();

        registry.fireDue(Instant.now().plusSeconds(1), bindingRuleEngine);

        Instant after = registry.nextWakeAt();
        assertThat(after).isAfter(before);
    }

    private static BindingRule periodicRule(String id, long periodicMs) {
        return new BindingRule(
                id,
                id,
                true,
                0,
                new BindingActivators(false, List.of(), null, periodicMs),
                "",
                "1.0",
                new BindingTarget("ignored", "value")
        );
    }
}
