package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ObjectBindingStatePortParseTest {

    private static final String PATH = "root.platform.devices.binding-state-parse";
    private static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    @Test
    void invalidBindingStateJsonIsNotReadAsEmpty() {
        ObjectBindingStatePort port = portWithState("{not-json");

        assertThatThrownBy(() -> port.previousDouble(PATH + "|alarm"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid binding state JSON");
    }

    @Test
    void nonObjectBindingStateJsonIsNotReadAsEmpty() {
        ObjectBindingStatePort port = portWithState("[]");

        assertThatThrownBy(() -> port.previousDouble(PATH + "|alarm"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid binding state JSON: expected object");
    }

    @Test
    void emptyObjectBindingStateHasNoPreviousValue() {
        ObjectBindingStatePort port = portWithState("{}");

        assertThat(port.previousDouble(PATH + "|alarm")).isEmpty();
    }

    @Test
    void blankBindingStateIsAnEmptyTree() {
        ObjectBindingStatePort port = portWithState("   ");

        assertThat(port.previousDouble(PATH + "|alarm")).isEmpty();
    }

    private static ObjectBindingStatePort portWithState(String json) {
        ObjectTree tree = new ObjectTree();
        PlatformObject node = new PlatformObject(
                "binding-state-parse",
                PATH,
                ObjectType.DEVICE,
                "parse",
                "",
                null
        );
        node.addVariable(new Variable(
                BindingStateVariables.BINDING_STATE,
                STRING_VALUE,
                true,
                true,
                DataRecord.single(STRING_VALUE, Map.of("value", json))
        ));
        tree.register(node);
        ObjectManager objectManager = mock(ObjectManager.class);
        when(objectManager.tree()).thenReturn(tree);
        when(objectManager.require(PATH)).thenReturn(node);
        return new ObjectBindingStatePort(objectManager, new ObjectMapper());
    }
}
