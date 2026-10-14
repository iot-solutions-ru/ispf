package com.ispf.server.ai.agent;

import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.EventLevel;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.config.IspfRoles;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentCourseCoercionTest {

    @Test
    void canonicalizesUppercaseOperatorRole() {
        assertEquals(IspfRoles.OPERATOR, AgentTreeTools.canonicalizeRoleName("OPERATOR"));
        assertEquals(IspfRoles.ADMIN, AgentTreeTools.canonicalizeRoleName("ROLE_ADMIN"));
        assertEquals(IspfRoles.DEVELOPER, AgentTreeTools.canonicalizeRoleName("Developer"));
        assertEquals("operator-readonly", AgentTreeTools.canonicalizeRoleName("operator_readonly"));
    }

    @Test
    void resolvesEvent1AliasToEvent1Id() {
        PlatformObject node = new PlatformObject(
                "virt-dev-1",
                "root.platform.devices.virt-dev-1",
                ObjectType.DEVICE,
                "virt-dev-1",
                "",
                "virtual-unified-v1"
        );
        DataSchema payload = DataSchema.builder("payload").field("int", FieldType.INTEGER).build();
        node.addEvent(new EventDescriptor("event1", "Unified event 1", payload, EventLevel.INFO));
        node.addEvent(new EventDescriptor("event2", "Unified event 2", payload, EventLevel.INFO));
        assertEquals("event1", AgentDiscoveryTools.resolveEventName(node, "Event1"));
        assertEquals("event1", AgentDiscoveryTools.resolveEventName(node, "Event 1"));
        assertEquals("event2", AgentDiscoveryTools.resolveEventName(node, "EVENT2"));
    }

    @Test
    void resolvesCourseVariableLabelsToCanonicalIds() {
        PlatformObject node = new PlatformObject(
                "virt-dev-1",
                "root.platform.devices.virt-dev-1",
                ObjectType.DEVICE,
                "virt-dev-1",
                "",
                "virtual-unified-v1"
        );
        DataSchema numeric = DataSchema.builder("v").field("value", FieldType.DOUBLE).build();
        node.addVariable(new Variable("intValue", numeric, true, true, null, false, null));
        node.addVariable(new Variable("floatValue", numeric, true, true, null, false, null));
        node.addVariable(new Variable("sineWave", numeric, false, true, null, false, null));
        node.addVariable(new Variable("table", numeric, false, true, null, false, null));

        assertEquals("intValue", AgentVariableNames.resolve(node, "Int"));
        assertEquals("intValue", AgentVariableNames.resolve(node, "INTEGER"));
        assertEquals("floatValue", AgentVariableNames.resolve(node, "Float"));
        assertEquals("sineWave", AgentVariableNames.resolve(node, "Sine Wave"));
        assertEquals("sineWave", AgentVariableNames.resolve(node, "sine"));
        assertEquals("table", AgentVariableNames.resolve(node, "Table"));
        assertEquals("intValue", AgentVariableNames.resolve(node, "intValue"));
    }
}
