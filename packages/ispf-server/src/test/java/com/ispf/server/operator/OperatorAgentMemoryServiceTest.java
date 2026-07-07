package com.ispf.server.operator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperatorAgentMemoryServiceTest {

    @Mock
    private OperatorAgentMemoryStore store;

    @InjectMocks
    private OperatorAgentMemoryService service;

    @Test
    void detectLocaleRussianFromCyrillic() {
        assertTrue(OperatorAgentMemoryService.detectLocale("Какая температура?").getLanguage().startsWith("ru"));
    }

    @Test
    void detectLocaleEnglishDefault() {
        assertTrue(OperatorAgentMemoryService.detectLocale("What is the temperature?").equals(Locale.ENGLISH));
    }

    @Test
    void formatPromptSectionUsesRussianHeader() {
        var record = new OperatorAgentMemoryRecord(
                "m1", "demo", "fact", "line 2", "GPU-03 is line 2", "op", null, 0,
                Instant.now(), Instant.now()
        );
        when(store.search(eq("demo"), eq("линия 2"), eq(14))).thenReturn(List.of(record));
        when(store.listForApp(eq("demo"), eq(14))).thenReturn(List.of());
        String section = service.formatPromptSection("demo", "линия 2", Locale.forLanguageTag("ru"));
        assertTrue(section.contains("Память приложения"));
        assertTrue(section.contains("GPU-03"));
    }

    @Test
    void formatPromptSectionUsesEnglishHeader() {
        var record = new OperatorAgentMemoryRecord(
                "m1", "demo", "fact", "line 2", "GPU-03 is line 2", "op", null, 0,
                Instant.now(), Instant.now()
        );
        when(store.search(eq("demo"), eq("line 2"), eq(14))).thenReturn(List.of(record));
        when(store.listForApp(eq("demo"), eq(14))).thenReturn(List.of());
        String section = service.formatPromptSection("demo", "line 2", Locale.ENGLISH);
        assertTrue(section.contains("Application memory"));
        assertFalse(section.contains("Память приложения"));
    }
}
