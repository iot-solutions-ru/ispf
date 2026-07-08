package com.ispf.server.query;

import com.ispf.core.object.ObjectType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class QueryDefinitionExecuteTest {

    @Autowired
    private QueryDefinitionService queryDefinitionService;

    @Test
    @Transactional
    void executeTreeScanFiltersByPathAndType() {
        QueryDefinitionService.QueryDefinition created = queryDefinitionService.create(
                new QueryDefinitionService.QueryDefinition(
                        "",
                        "device-type-scan",
                        "Device type scan",
                        "Devices only",
                        "tree-scan",
                        "root.platform.devices.*",
                        """
                                {
                                  "objectTypes": ["DEVICE"],
                                  "fields": [
                                    {"name": "path", "source": "path"},
                                    {"name": "type", "source": "type"}
                                  ]
                                }
                                """,
                        "",
                        true,
                        "",
                        ""
                )
        );

        QueryDefinitionService.QueryExecuteResult result = queryDefinitionService.execute(created.path());

        assertThat(result.rowCount()).isGreaterThan(0);
        assertThat(result.rows()).allSatisfy(row -> {
            assertThat(row.get("type")).isEqualTo(ObjectType.DEVICE.name());
            assertThat((String) row.get("path")).startsWith("root.platform.devices.");
        });
        assertThat(queryDefinitionService.getByPath(created.path()).lastRunAt()).isNotBlank();
    }
}
