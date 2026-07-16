package com.ispf.server.object;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class BindingRulesCacheTest {

    private static final DataSchema DOUBLE = DataSchema.builder("v")
            .field("value", FieldType.DOUBLE)
            .build();

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private BindingRulesService bindingRulesService;

    @Test
    void listRulesUsesCacheUntilSaveInvalidates() {
        String name = "cache-bind-" + System.nanoTime();
        String path = "root.platform.devices." + name;
        objectManager.create("root.platform.devices", name, ObjectType.DEVICE, "Cache bind", null, null);
        objectManager.createVariable(
                path,
                "out",
                DOUBLE,
                true,
                false,
                DataRecord.single(DOUBLE, Map.of("value", 0.0)),
                false,
                null
        );

        bindingRulesService.saveRules(path, List.of(
                new BindingRule(
                        "r1",
                        "rule",
                        true,
                        0,
                        BindingActivators.onLocalChange(),
                        "",
                        "1.0",
                        new BindingTarget("out", "value")
                )
        ));

        List<BindingRule> first = bindingRulesService.listRules(path);
        List<BindingRule> second = bindingRulesService.listRules(path);
        assertThat(second).isSameAs(first);

        bindingRulesService.saveRules(path, List.of(
                new BindingRule(
                        "r1",
                        "rule-2",
                        true,
                        0,
                        BindingActivators.onLocalChange(),
                        "",
                        "2.0",
                        new BindingTarget("out", "value")
                )
        ));
        List<BindingRule> third = bindingRulesService.listRules(path);
        assertThat(third).isNotSameAs(first);
        assertThat(third.getFirst().name()).isEqualTo("rule-2");
    }
}
