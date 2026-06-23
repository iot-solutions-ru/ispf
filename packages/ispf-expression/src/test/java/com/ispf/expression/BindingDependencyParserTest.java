package com.ispf.expression;

import com.ispf.core.binding.BindingVariableRef;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindingDependencyParserTest {

    @Test
    void parsesRefAtDependencies() {
        String expression = "refAt(\"root.platform.devices.dev-03\", sineWave) + refAt(\"root.platform.devices.dev-02\", sineWave)";
        Set<BindingVariableRef> refs = BindingDependencyParser.parseRefAtDependencies(expression);
        assertEquals(2, refs.size());
        assertTrue(refs.contains(BindingVariableRef.remote("root.platform.devices.dev-03", "sineWave")));
        assertTrue(refs.contains(BindingVariableRef.remote("root.platform.devices.dev-02", "sineWave")));
    }
}
