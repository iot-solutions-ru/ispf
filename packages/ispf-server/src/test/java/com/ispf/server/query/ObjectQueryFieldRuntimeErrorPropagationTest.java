package com.ispf.server.query;

import com.ispf.core.object.ObjectType;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.query.oq.ObjectQuerySpec;
import com.ispf.server.query.oq.ObjectQuerySpecParser;
import com.ispf.server.security.acl.VariableAclRequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ObjectQueryFieldRuntimeErrorPropagationTest {

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
    void fieldExpressionFailureFailsTheQueryWithoutMemberAcl() {
        devicePath = createDevice("oq-field-err-sys-");
        ObjectQuerySpec spec = badExpressionSpec(devicePath);

        assertThatThrownBy(() -> objectQueryService.execute(spec, "root.platform"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("no_such_name");
    }

    @Test
    void fieldExpressionFailureFailsTheQueryUnderMemberAcl() {
        devicePath = createDevice("oq-field-err-mem-");
        ObjectQuerySpec spec = badExpressionSpec(devicePath);
        var operator = UsernamePasswordAuthenticationToken.authenticated("operator", "n/a", List.of());

        assertThatThrownBy(() -> VariableAclRequestContext.callAsMember(
                operator,
                () -> objectQueryService.execute(spec, "root.platform")
        ))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("no_such_name");
    }

    private String createDevice(String prefix) {
        String name = prefix + System.nanoTime();
        String path = "root.platform.devices." + name;
        objectManager.create("root.platform.devices", name, ObjectType.DEVICE, name, "", null);
        return path;
    }

    private ObjectQuerySpec badExpressionSpec(String path) {
        return new ObjectQuerySpecParser(objectMapper).parse("""
                {
                  "from": {
                    "sourcePathPattern": "%s",
                    "objectTypes": ["DEVICE"]
                  },
                  "fields": [
                    {"name": "path", "source": "path", "alias": "row"},
                    {"name": "broken", "expression": "no_such_name"}
                  ]
                }
                """.formatted(path));
    }
}
