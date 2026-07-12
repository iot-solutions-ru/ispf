package com.ispf.server.query;

import com.ispf.core.object.ObjectType;
import com.ispf.server.query.oq.ObjectQueryResult;
import com.ispf.server.query.oq.ObjectQuerySpec;
import com.ispf.server.query.oq.ObjectQuerySpecParser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ObjectQueryServiceTest {

    @Autowired
    private ObjectQueryService objectQueryService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Transactional(readOnly = true)
    void variablesSourceListsVariableNames() {
        ObjectQuerySpecParser parser = new ObjectQuerySpecParser(objectMapper);
        ObjectQuerySpec spec = parser.parse("""
                {
                  "from": {
                    "sourcePathPattern": "root.platform.devices.demo-sensor-01",
                    "objectTypes": ["DEVICE"]
                  },
                  "fields": [
                    {"name": "path", "source": "path", "alias": "row"},
                    {"name": "vars", "source": "variables", "alias": "row"}
                  ]
                }
                """);
        ObjectQueryResult result = objectQueryService.execute(spec, "root.platform");
        assertThat(result.rowCount()).isEqualTo(1);
        assertThat(result.rows().getFirst().get("vars")).isInstanceOf(List.class);
        assertThat((List<?>) result.rows().getFirst().get("vars")).isNotEmpty();
    }

    @Test
    @Transactional(readOnly = true)
    void parentJoinAddsParentPath() {
        ObjectQuerySpecParser parser = new ObjectQuerySpecParser(objectMapper);
        ObjectQuerySpec spec = parser.parse("""
                {
                  "from": {
                    "sourcePathPattern": "root.platform.devices.*",
                    "objectTypes": ["DEVICE"]
                  },
                  "joins": [
                    {"alias": "parent", "type": "left", "on": {"kind": "parent"}}
                  ],
                  "fields": [
                    {"name": "path", "source": "path", "alias": "row"},
                    {"name": "parentPath", "source": "path", "alias": "parent"}
                  ],
                  "limit": 5
                }
                """);
        ObjectQueryResult result = objectQueryService.execute(spec, "root.platform");
        assertThat(result.rowCount()).isGreaterThan(0);
        assertThat(result.rows()).allSatisfy(row -> {
            String path = String.valueOf(row.get("path"));
            String parentPath = String.valueOf(row.get("parentPath"));
            assertThat(parentPath).isEqualTo(path.substring(0, path.lastIndexOf('.')));
        });
    }

    @Test
    @Transactional(readOnly = true)
    void legacyTreeScanMatchesDevices() {
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

        ObjectQueryResult result = objectQueryService.execute(spec, "root.platform.queries.test");

        assertThat(result.rowCount()).isGreaterThan(0);
        assertThat(result.rows()).allSatisfy(row -> {
            assertThat(row.get("type")).isEqualTo(ObjectType.DEVICE.name());
            assertThat((String) row.get("path")).startsWith("root.platform.devices.");
        });
    }

    @Test
    void groupByAggregatesDeviceCount() {
        ObjectQuerySpecParser parser = new ObjectQuerySpecParser(objectMapper);
        ObjectQuerySpec spec = parser.parse("""
                {
                  "from": {
                    "sourcePathPattern": "root.platform.devices.*",
                    "objectTypes": ["DEVICE"]
                  },
                  "fields": [
                    {"name": "type", "source": "type", "alias": "row"}
                  ],
                  "groupBy": ["type"],
                  "aggregates": [
                    {"name": "deviceCount", "fn": "count"}
                  ]
                }
                """);
        ObjectQueryResult result = objectQueryService.execute(spec, "root.platform");
        assertThat(result.rowCount()).isEqualTo(1);
        assertThat(result.rows().getFirst().get("type")).isEqualTo(ObjectType.DEVICE.name());
        assertThat(result.rows().getFirst().get("deviceCount")).isInstanceOf(Integer.class);
        assertThat((Integer) result.rows().getFirst().get("deviceCount")).isGreaterThan(0);
    }
}
