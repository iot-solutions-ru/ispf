package com.ispf.server.ai.context;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContextPackSearchServiceExampleAliasTest {

    @Mock
    private ContextPackService contextPackService;

    @InjectMocks
    private ContextPackSearchService searchService;

    @Test
    void resolvesVirtualAliasToLabTraining() {
        when(contextPackService.loadPack()).thenReturn(Map.of(
                "examples", List.of(Map.of(
                        "appId", "lab-training",
                        "manifest", Map.of("description", "Lab training bundle")
                ))
        ));

        Map<String, Object> result = searchService.exampleBundle("virtual", List.of());

        assertThat(result.get("status")).isEqualTo("OK");
        assertThat(result.get("resolvedAppId")).isEqualTo("lab-training");
    }
}
