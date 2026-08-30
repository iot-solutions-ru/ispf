package com.ispf.server.query;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.query.oq.ObjectQueryResult;
import com.ispf.server.query.oq.ObjectQuerySpec;
import com.ispf.server.query.oq.ObjectQuerySpecParser;
import com.ispf.server.security.acl.VariableAclRequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ObjectQueryServiceTest {

    @Autowired
    private ObjectQueryService objectQueryService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ObjectManager objectManager;

    private String aclObjectPath;

    @AfterEach
    void cleanup() {
        if (aclObjectPath != null && objectManager.tree().findByPath(aclObjectPath).isPresent()) {
            objectManager.delete(aclObjectPath);
        }
        aclObjectPath = null;
    }

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

    @Test
    void memberQueryOmitsRestrictedLiveAndHistorianColumns() {
        String name = "oq-acl-" + System.nanoTime();
        aclObjectPath = "root.platform.devices." + name;
        objectManager.create(
                "root.platform.devices",
                name,
                ObjectType.DEVICE,
                "OQ ACL test",
                "",
                null
        );
        DataSchema schema = DataSchema.builder("secret")
                .field("value", FieldType.DOUBLE)
                .build();
        objectManager.createVariable(
                aclObjectPath,
                "secret",
                schema,
                true,
                true,
                DataRecord.single(schema, Map.of("value", 8675309.0)),
                true,
                null,
                List.of("engineer"),
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
                    {"name": "vars", "source": "variables", "alias": "row"},
                    {"name": "sourceSecret", "source": "secret", "alias": "row"},
                    {"name": "refSecret", "ref": "{row}/secret/value"},
                    {"name": "expressionSecret", "expression": "self.secret.value"},
                    {
                      "name": "historianSecret",
                      "ref": "{row}/secret",
                      "historian": {"fn": "latest", "window": "15m"}
                    }
                  ]
                }
                """.formatted(aclObjectPath));
        var operator = UsernamePasswordAuthenticationToken.authenticated(
                "operator",
                "n/a",
                List.of()
        );

        ObjectQueryResult memberResult = VariableAclRequestContext.callAsMember(
                operator,
                () -> objectQueryService.execute(spec, "root.platform")
        );

        assertThat(memberResult.rowCount()).isEqualTo(1);
        assertThat(memberResult.rows().getFirst())
                .containsEntry("path", aclObjectPath)
                .doesNotContainKeys(
                        "sourceSecret",
                        "refSecret",
                        "expressionSecret",
                        "historianSecret"
                );
        assertThat(String.valueOf(memberResult.rows().getFirst().get("vars")))
                .doesNotContain("secret");

        ObjectQueryResult systemResult = objectQueryService.execute(spec, "root.platform");
        assertThat(systemResult.rows().getFirst())
                .containsKeys("sourceSecret", "refSecret", "expressionSecret", "historianSecret");
        assertThat(systemResult.rows().getFirst().toString()).contains("8675309");

        ObjectQuerySpec expandSpec = new ObjectQuerySpecParser(objectMapper).parse("""
                {
                  "from": {
                    "sourcePathPattern": "%s",
                    "objectTypes": ["DEVICE"],
                    "expand": {"variable": "secret"}
                  },
                  "fields": [
                    {"name": "secretValue", "ref": "{row}/value"}
                  ]
                }
                """.formatted(aclObjectPath));
        ObjectQueryResult memberExpand = VariableAclRequestContext.callAsMember(
                operator,
                () -> objectQueryService.execute(expandSpec, "root.platform")
        );
        assertThat(memberExpand.rows()).isEmpty();
        assertThat(objectQueryService.execute(expandSpec, "root.platform").rows())
                .singleElement()
                .satisfies(row -> assertThat(row).containsEntry("secretValue", 8675309.0));

        ObjectQuerySpec filterSpec = new ObjectQuerySpecParser(objectMapper).parse("""
                {
                  "from": {
                    "sourcePathPattern": "%s",
                    "objectTypes": ["DEVICE"],
                    "filter": "self.secret.value == 8675309.0"
                  },
                  "fields": [
                    {"name": "path", "source": "path", "alias": "row"}
                  ]
                }
                """.formatted(aclObjectPath));
        ObjectQueryResult memberFilter = VariableAclRequestContext.callAsMember(
                operator,
                () -> objectQueryService.execute(filterSpec, "root.platform")
        );
        assertThat(memberFilter.rows()).isEmpty();
        assertThat(objectQueryService.execute(filterSpec, "root.platform").rows()).hasSize(1);
    }
}
