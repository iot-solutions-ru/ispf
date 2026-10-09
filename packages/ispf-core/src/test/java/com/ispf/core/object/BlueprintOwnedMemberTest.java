package com.ispf.core.object;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlueprintOwnedMemberTest {

    @Test
    void rejectsDeleteOfContributedMembers() {
        PlatformObject dashboard = dashboard();
        dashboard.putBlueprintContribution("mix-1", new BlueprintContribution(
                List.of("mixVar1"),
                List.of("mixAlarm"),
                List.of("reset"),
                List.of("rule-1")
        ));

        assertThatThrownBy(() -> BlueprintOwnedMember.assertDeletableVariable(dashboard, "mixVar1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mix-1");
        assertThatThrownBy(() -> BlueprintOwnedMember.assertDeletableEvent(dashboard, "mixAlarm"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BlueprintOwnedMember.assertDeletableFunction(dashboard, "reset"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BlueprintOwnedMember.assertDeletableBindingRule(dashboard, "rule-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsDeleteOfMembersTheObjectOwns() {
        PlatformObject dashboard = dashboard();

        assertThatCode(() -> BlueprintOwnedMember.assertDeletableVariable(dashboard, "localNote")).doesNotThrowAnyException();
        assertThatCode(() -> BlueprintOwnedMember.assertDeletableEvent(dashboard, "local")).doesNotThrowAnyException();
        assertThatCode(() -> BlueprintOwnedMember.assertDeletableFunction(dashboard, "localFn")).doesNotThrowAnyException();
        assertThatCode(() -> BlueprintOwnedMember.assertDeletableBindingRule(dashboard, "local-rule")).doesNotThrowAnyException();
    }

    private static PlatformObject dashboard() {
        return new PlatformObject("d1", "root.platform.dashboards.dashOne", ObjectType.DASHBOARD, "dashOne", "", null);
    }
}
