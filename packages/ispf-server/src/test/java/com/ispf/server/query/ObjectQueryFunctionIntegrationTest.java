package com.ispf.server.query;

import com.ispf.core.object.ObjectType;
import com.ispf.server.function.FunctionService;
import com.ispf.server.function.ObjectQueryFunctionHandler;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.query.oq.ObjectQuerySpec;
import com.ispf.server.query.oq.ObjectQuerySpecParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ObjectQueryFunctionIntegrationTest {

    private static final String QUERIES_ROOT = ObjectQueryCatalog.QUERIES_ROOT;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private FunctionService functionService;

    @Autowired
    private ObjectQueryFunctionHandler objectQueryFunctionHandler;

    @Autowired
    private ObjectMapper objectMapper;

    private String objectPath;

    @AfterEach
    void cleanup() {
        if (objectPath != null) {
            objectManager.delete(objectPath);
            objectPath = null;
        }
    }

    @Test
    void invokeRunReturnsDeviceRows() {
        String name = "oq-it-" + System.nanoTime();
        objectPath = QUERIES_ROOT + "." + name;
        objectManager.create(QUERIES_ROOT, name, ObjectType.CUSTOM, name, "integration test", null);
        ObjectQuerySpecParser parser = new ObjectQuerySpecParser(objectMapper);
        ObjectQuerySpec spec = parser.parse("""
                {
                  "from": {
                    "sourcePathPattern": "root.platform.devices.*",
                    "objectTypes": ["DEVICE"]
                  },
                  "fields": [
                    {"name": "path", "source": "path", "alias": "row"},
                    {"name": "type", "source": "type", "alias": "row"}
                  ]
                }
                """);
        objectManager.upsertFunction(objectPath, ObjectQueryFunctionSupport.runFunction(parser.writeSpec(spec)));

        assertThat(objectQueryFunctionHandler.supports(objectPath, "run")).isTrue();
        var result = functionService.invoke(objectPath, "run", (com.ispf.core.model.DataRecord) null);
        assertThat(result.firstRow().get("rowCount")).isInstanceOf(Integer.class);
        assertThat((Integer) result.firstRow().get("rowCount")).isGreaterThan(0);
        assertThat(String.valueOf(result.firstRow().get("rows"))).contains("root.platform.devices.");
    }
}
