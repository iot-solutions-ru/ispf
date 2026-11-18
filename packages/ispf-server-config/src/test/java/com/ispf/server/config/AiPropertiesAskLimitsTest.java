package com.ispf.server.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AiPropertiesAskLimitsTest {

    @Test
    void askMaxTokensDefaultsBelowExecuteCeiling() {
        AiProperties props = new AiProperties();
        assertEquals(2_048, props.getAgentAskMaxTokens());
        assertEquals(8_192, props.getAgentMaxTokens());
        assertEquals(6, props.getAgentAskMaxHistoryTurns());
        assertFalse(props.isAgentAskNativeTools());
    }

    @Test
    void askMaxTokensNeverExceedsExecuteCap() {
        AiProperties props = new AiProperties();
        props.setAgentMaxTokens(1_024);
        props.setAgentAskMaxTokens(4_096);
        assertEquals(1_024, props.getAgentAskMaxTokens());
    }
}
