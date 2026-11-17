package com.ispf.server.plugin.blueprint;

import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.object.BindingRulesService;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlueprintStartupRulesHealthIndicatorTest {

    @Test
    void upWhenNoProblems() {
        BlueprintStartupRulesHealth health = new BlueprintStartupRulesHealth(
                mock(ObjectManager.class),
                mock(BindingRulesService.class)
        );
        BlueprintStartupRulesHealthIndicator indicator = new BlueprintStartupRulesHealthIndicator(health);
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void downWhenUnresolvedRulesReported() {
        String path = "root.obj";
        ObjectManager objectManager = mock(ObjectManager.class);
        ObjectTree tree = mock(ObjectTree.class);
        when(objectManager.tree()).thenReturn(tree);
        when(tree.findByPath(path)).thenReturn(Optional.of(
                new PlatformObject("obj", path, ObjectType.CUSTOM, "Obj", "", "cold-chain")
        ));
        BlueprintStartupRulesHealth health = new BlueprintStartupRulesHealth(
                objectManager,
                mock(BindingRulesService.class)
        );
        health.reportAll(List.of(new BlueprintStartupRulesHealth.Problem(
                path, "limit", "cold-chain", "cold-chain", BlueprintStartupRulesHealth.Kind.MISSING
        )));
        BlueprintStartupRulesHealthIndicator indicator = new BlueprintStartupRulesHealthIndicator(health);
        Health result = indicator.health();
        assertThat(result.getStatus()).isEqualTo(Status.DOWN);
        assertThat(result.getDetails().get("unresolvedRules")).asList()
                .containsExactly("root.obj#limit: templated blueprint rule has no saved instance rule");
    }
}
