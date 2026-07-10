package com.ispf.server.platform.analytics.formula;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingRuleKind;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.object.BindingRulesService;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BindingFormulaRebindServiceTest {

    @Mock
    private ObjectManager objectManager;

    @Mock
    private BindingRulesService bindingRulesService;

    @Mock
    private ObjectTree tree;

    @InjectMocks
    private BindingFormulaRebindService rebindService;

    @Test
    void rebindsObjectsWithMatchingFormulaRef() {
        PlatformObject device = org.mockito.Mockito.mock(PlatformObject.class);
        when(device.path()).thenReturn("root.platform.devices.tank-a");
        when(objectManager.tree()).thenReturn(tree);
        when(tree.all()).thenReturn(List.of(device));

        BindingRule rule = new BindingRule(
                "fill-rate",
                "fill-rate",
                true,
                0,
                BindingRuleKind.HISTORIAN,
                new BindingActivators(false, null, null, 60_000L, false, false),
                "",
                "stale-expression",
                new BindingTarget("variable", "rate", "value", null, null),
                "1h",
                null,
                "tank-fill-rate",
                Map.of("levelPath", "root.dev.tank.level"),
                AnalyticsFormula.SCOPE_SITE,
                null
        );
        when(bindingRulesService.listRules("root.platform.devices.tank-a")).thenReturn(List.of(rule));

        int rebound = rebindService.rebindFormulaReferences("tank-fill-rate", AnalyticsFormula.SCOPE_SITE, null);

        assertThat(rebound).isEqualTo(1);
        verify(bindingRulesService).saveRules(eq("root.platform.devices.tank-a"), eq(List.of(rule)));
    }
}
