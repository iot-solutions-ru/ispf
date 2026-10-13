package com.ispf.server.ai.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextPackHashEmbedderTest {

    @Test
    void similarTextsHaveHigherCosineThanUnrelated() {
        float[] snmp = ContextPackHashEmbedder.embed("snmp agent device monitoring dashboard oid community");
        float[] snmpNear = ContextPackHashEmbedder.embed("SNMP host monitoring with OID mappings");
        float[] unrelated = ContextPackHashEmbedder.embed("workflow bpmn correlator alarm shelf");

        double near = ContextPackHashEmbedder.cosine(snmp, snmpNear);
        double far = ContextPackHashEmbedder.cosine(snmp, unrelated);
        assertTrue(near > far, "near=" + near + " far=" + far);
        assertTrue(near > 0.05);
    }

    @Test
    void modelIdRecognized() {
        assertTrue(ContextPackHashEmbedder.isHashModel("ispf-hash-v1"));
        assertEquals(384, ContextPackHashEmbedder.DIMS);
    }
}
