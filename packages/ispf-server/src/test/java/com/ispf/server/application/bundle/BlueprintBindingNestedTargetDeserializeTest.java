package com.ispf.server.application.bundle;

import com.ispf.plugin.blueprint.BlueprintBindingRule;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleBlueprint;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class BlueprintBindingNestedTargetDeserializeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void bundleBlueprintAcceptsLiveNestedTarget() throws Exception {
        String json = """
                {
                  "name": "hub-v1",
                  "type": "SINGLETON",
                  "bindings": [
                    {
                      "id": "copy-status",
                      "name": "copy-status",
                      "enabled": true,
                      "expression": "self.temperature.value",
                      "target": { "variableName": "status", "field": "value" }
                    }
                  ]
                }
                """;

        BundleBlueprint blueprint = objectMapper.readValue(json, BundleBlueprint.class);
        BlueprintBindingRule rule = blueprint.bindings().getFirst();

        assertThat(rule.targetVariable()).isEqualTo("status");
        assertThat(rule.targetField()).isEqualTo("value");
        assertThatCode(rule::toBindingRule).doesNotThrowAnyException();
        assertThat(rule.toBindingRule().target().variableName()).isEqualTo("status");
    }
}
