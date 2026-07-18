package com.ispf.server.workflow;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowPublishedToolNamingTest {

    @Test
    void mcpToolNameIsStableAndCollisionSafe() throws Exception {
        Method method = WorkflowService.class.getDeclaredMethod("mcpToolName", String.class, Set.class);
        method.setAccessible(true);
        Set<String> used = new HashSet<>();
        String first = (String) method.invoke(null, "root.platform.workflows.alarm-triage", used);
        String second = (String) method.invoke(null, "root.apps.other.alarm-triage", used);
        assertThat(first).isEqualTo("wf_alarm_triage");
        assertThat(second).startsWith("wf_alarm_triage_");
        assertThat(second).isNotEqualTo(first);
    }
}
