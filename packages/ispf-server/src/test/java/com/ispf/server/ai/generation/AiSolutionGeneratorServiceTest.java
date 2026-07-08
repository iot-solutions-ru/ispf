package com.ispf.server.ai.generation;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiSolutionGeneratorServiceTest {

    private final AiSolutionGeneratorService service = new AiSolutionGeneratorService();

    @Test
    void detectsMesScadaAndHvacKeywords() {
        assertEquals("mes", service.generate("Deploy MES OEE dashboard").get("domain"));
        assertEquals("scada", service.generate("SCADA pump station mimic overview").get("domain"));
        assertEquals("hvac", service.generate("HVAC zone comfort setpoints").get("domain"));
    }

    @Test
    void blueprintDraftIncludesSpecBriefAndArtifacts() {
        Map<String, Object> result = service.generate("SCADA facility with historian trends");
        @SuppressWarnings("unchecked")
        Map<String, Object> draft = (Map<String, Object>) result.get("blueprintDraft");
        assertEquals("scada", draft.get("domain"));
        assertEquals("stub", result.get("mode"));
    }

    @Test
    void rejectsBlankPrompt() {
        assertThrows(IllegalArgumentException.class, () -> service.generate("  "));
    }
}
