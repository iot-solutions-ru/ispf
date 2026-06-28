package com.ispf.server.operator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class OperatorAgentMemoryLearnerTest {

    @Mock
    private OperatorAgentMemoryService memoryService;

    @InjectMocks
    private OperatorAgentMemoryLearner learner;

    @Test
    void extractsRememberPhrase() {
        var drafts = learner.extractDrafts("Запомни: линия 2 — это GPU-03", null);
        assertEquals(1, drafts.size());
        assertEquals("fact", drafts.getFirst().kind());
        assertTrue(drafts.getFirst().content().contains("GPU-03"));
    }

    @Test
    void extractsCorrection() {
        var drafts = learner.extractDrafts("Нет, имел в виду датчик inlet_temperature на линии 2", null);
        assertEquals(1, drafts.size());
        assertEquals("correction", drafts.getFirst().kind());
    }
}
