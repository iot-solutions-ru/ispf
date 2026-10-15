package com.ispf.server.ai.agent;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.object.ObjectTreePort;
import com.ispf.server.security.acl.VariableMemberAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentTreeToolsSetVariableTest {

    @Mock
    private ObjectTreePort objectTreePort;
    @Mock
    private VariableMemberAccessService variableMemberAccessService;

    @Test
    void setVariableKeepsJsonLookingStringAsScalar() throws Exception {
        String path = "root.platform.devices.flat-json";
        DataSchema schema = DataSchema.builder("agentVar").field("value", FieldType.STRING).build();
        Variable existing = new Variable("stateJson", schema, true, true, null);
        PlatformObject node = new PlatformObject("1", path, ObjectType.DEVICE, "flat", "", null);
        node.addVariable(existing);
        when(objectTreePort.require(path)).thenReturn(node);
        when(objectTreePort.setVariableValue(eq(path), eq("stateJson"), any())).thenAnswer(inv ->
                new Variable("stateJson", schema, true, true, inv.getArgument(2))
        );

        PlatformAgentTool tool = AgentTreeTools.all(
                objectTreePort,
                null,
                variableMemberAccessService,
                null,
                null,
                new ObjectMapper(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ).stream().filter(item -> "set_variable".equals(item.name())).findFirst().orElseThrow();

        Map<String, Object> result = tool.execute(
                Map.of("path", path, "name", "stateJson", "value", "{\"action\":\"NEW_GAME\"}"),
                new AgentContext("admin", null, null)
        );

        assertEquals("OK", result.get("status"));
        ArgumentCaptor<DataRecord> record = ArgumentCaptor.forClass(DataRecord.class);
        verify(objectTreePort).setVariableValue(eq(path), eq("stateJson"), record.capture());
        assertEquals("{\"action\":\"NEW_GAME\"}", record.getValue().firstRow().get("value"));
    }
}
