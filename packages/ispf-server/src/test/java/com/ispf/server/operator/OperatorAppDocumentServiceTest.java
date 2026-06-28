package com.ispf.server.operator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class OperatorAppDocumentServiceTest {

    @Mock
    private OperatorAppDocumentStore store;

    @InjectMocks
    private OperatorAppDocumentService service;

    @Test
    void formatPromptSectionIncludesInstructions() {
        String section = service.formatPromptSection("demo", "pump", "Always answer in Russian and cite SOP-12.");
        assertTrue(section.contains("Operator instructions"));
        assertTrue(section.contains("SOP-12"));
    }

    @Test
    void formatPromptSectionEmptyWhenNoData() {
        String section = service.formatPromptSection("demo", "", "");
        assertFalse(section.contains("knowledge base"));
    }
}
