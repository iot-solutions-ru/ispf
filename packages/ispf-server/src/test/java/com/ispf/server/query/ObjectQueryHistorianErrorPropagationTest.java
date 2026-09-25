package com.ispf.server.query;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.server.history.VariableHistoryService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.query.oq.ObjectQuerySpec;
import com.ispf.server.query.oq.ObjectQuerySpecParser;
import com.ispf.server.security.acl.VariableMemberAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Import(ObjectQueryHistorianErrorPropagationTest.FailingHistorianConfig.class)
class ObjectQueryHistorianErrorPropagationTest {

    @Autowired
    private ObjectQueryService objectQueryService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ObjectManager objectManager;

    private String devicePath;

    @AfterEach
    void cleanup() {
        if (devicePath != null && objectManager.tree().findByPath(devicePath).isPresent()) {
            objectManager.delete(devicePath);
        }
        devicePath = null;
    }

    @Test
    void historianReadFailureFailsTheQuery() {
        String name = "oq-hist-err-" + System.nanoTime();
        devicePath = "root.platform.devices." + name;
        objectManager.create(
                "root.platform.devices",
                name,
                ObjectType.DEVICE,
                name,
                "",
                null
        );
        DataSchema schema = DataSchema.builder("temperature")
                .field("value", FieldType.DOUBLE)
                .build();
        objectManager.createVariable(
                devicePath,
                "temperature",
                schema,
                true,
                true,
                DataRecord.single(schema, Map.of("value", 21.5)),
                true,
                null,
                List.of(),
                List.of()
        );

        ObjectQuerySpec spec = new ObjectQuerySpecParser(objectMapper).parse("""
                {
                  "from": {
                    "sourcePathPattern": "%s",
                    "objectTypes": ["DEVICE"]
                  },
                  "fields": [
                    {"name": "path", "source": "path", "alias": "row"},
                    {
                      "name": "latestTemp",
                      "ref": "{row}/temperature",
                      "historian": {"fn": "latest", "window": "15m"}
                    }
                  ]
                }
                """.formatted(devicePath));

        assertThatThrownBy(() -> objectQueryService.execute(spec, "root.platform"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("historian unavailable");
    }

    @TestConfiguration
    static class FailingHistorianConfig {

        @Bean
        @Primary
        ObjectQueryHistorianColumnResolver failingHistorianColumnResolver(
                VariableMemberAccessService variableMemberAccessService
        ) {
            @SuppressWarnings("unchecked")
            ObjectProvider<VariableHistoryService> provider = mock(ObjectProvider.class);
            VariableHistoryService history = mock(VariableHistoryService.class);
            when(provider.getIfAvailable()).thenReturn(history);
            when(history.query(anyString(), anyString(), any(), any(), any(), anyInt()))
                    .thenThrow(new IllegalStateException("historian unavailable"));
            return new ObjectQueryHistorianColumnResolver(provider, variableMemberAccessService);
        }
    }
}
