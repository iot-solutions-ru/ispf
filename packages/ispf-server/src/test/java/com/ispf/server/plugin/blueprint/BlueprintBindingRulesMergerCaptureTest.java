package com.ispf.server.plugin.blueprint;

import com.ispf.core.binding.BindingRule;
import com.ispf.core.object.ObjectType;
import com.ispf.plugin.blueprint.BlueprintBindingRule;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintType;
import com.ispf.server.object.BindingRulesService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlueprintBindingRulesMergerCaptureTest {

    @Test
    void captureFromObjectKeepsSourceBindingRulesAndNamesOmissions() {
        BindingRulesService bindingRulesService = mock(BindingRulesService.class);
        BindingRule live = BlueprintBindingRule.of("rule-copy", "temperature", "1 + 1").toBindingRule();
        when(bindingRulesService.listRules("root.platform.devices.src")).thenReturn(List.of(live));
        BlueprintBindingRulesMerger merger = new BlueprintBindingRulesMerger(
                bindingRulesService, null, null, null, null, null
        );
        BlueprintDefinition snapshot = new BlueprintDefinition(
                "bp-1",
                "from-src",
                "Created from root.platform.devices.src",
                BlueprintType.INSTANCE,
                ObjectType.DEVICE,
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Instant.parse("2026-10-13T00:00:00Z"),
                Instant.parse("2026-10-13T00:00:00Z")
        );

        BlueprintDefinition captured = merger.captureFromObject("root.platform.devices.src", snapshot);

        assertEquals(1, captured.bindingRules().size());
        assertEquals("rule-copy", captured.bindingRules().getFirst().id());
        assertEquals("temperature", captured.bindingRules().getFirst().targetVariable());
        assertEquals("1 + 1", captured.bindingRules().getFirst().expression());
        assertEquals(
                BlueprintBindingRulesMerger.NOT_EXPORTED_SECTIONS,
                captured.parameters().get(BlueprintBindingRulesMerger.NOT_EXPORTED)
        );
    }
}
