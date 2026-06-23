package com.ispf.server.object;

import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingRulesConstants;
import com.ispf.core.object.PlatformObject;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One-time migration of legacy {@code binding_expr} column values into {@code @bindingRules}.
 */
@Component
public class BindingExpressionMigrationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final BindingRulesService bindingRulesService;
    private final ObjectManager objectManager;

    public BindingExpressionMigrationRunner(
            JdbcTemplate jdbcTemplate,
            BindingRulesService bindingRulesService,
            @Lazy ObjectManager objectManager
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.bindingRulesService = bindingRulesService;
        this.objectManager = objectManager;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    @Transactional
    public void migrateLegacyBindings() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT object_path, name, binding_expr FROM object_variables WHERE binding_expr IS NOT NULL AND binding_expr <> ''"
        );
        if (rows.isEmpty()) {
            return;
        }
        Map<String, List<BindingRule>> rulesByObject = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String objectPath = String.valueOf(row.get("object_path"));
            String name = String.valueOf(row.get("name"));
            String expression = String.valueOf(row.get("binding_expr"));
            if (BindingRulesConstants.isReservedVariable(name)
                    || BindingStateVariables.BINDING_STATE.equals(name)) {
                continue;
            }
            rulesByObject.computeIfAbsent(objectPath, ignored -> new ArrayList<>())
                    .add(BindingRulesService.fromLegacyExpression(name, expression));
        }
        for (Map.Entry<String, List<BindingRule>> entry : rulesByObject.entrySet()) {
            String objectPath = entry.getKey();
            if (objectManager.tree().findByPath(objectPath).isEmpty()) {
                continue;
            }
            List<BindingRule> merged = new ArrayList<>(bindingRulesService.listRules(objectPath));
            for (BindingRule migrated : entry.getValue()) {
                merged.removeIf(rule -> rule.id().equals(migrated.id()));
                merged.add(migrated);
            }
            bindingRulesService.saveRules(objectPath, merged);
        }
        jdbcTemplate.update("UPDATE object_variables SET binding_expr = NULL WHERE binding_expr IS NOT NULL");
    }
}
