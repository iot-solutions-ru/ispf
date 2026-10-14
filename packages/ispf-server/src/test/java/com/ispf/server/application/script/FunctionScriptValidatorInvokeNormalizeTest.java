package com.ispf.server.application.script;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FunctionScriptValidatorInvokeNormalizeTest {

    private final FunctionScriptValidator validator = new FunctionScriptValidator(new ObjectMapper());

    @Test
    void normalizesFunctionAndPathAliasesAndDefaultsObjectPath() {
        String raw = """
                {"steps":[
                  {"type":"invoke","path":"root.platform.devices.v1","function":"echo","input":{},"var":"out"},
                  {"type":"return","fields":{"ok":true}}
                ]}
                """;
        String normalized = validator.normalizeAliases(raw);
        assertThat(normalized).contains("\"type\":\"invoke_function\"");
        assertThat(normalized).contains("\"objectPath\":\"root.platform.devices.v1\"");
        assertThat(normalized).contains("\"functionName\":\"echo\"");
        assertThatCode(() -> validator.validate(normalized)).doesNotThrowAnyException();
    }

    @Test
    void doesNotInventSelfForCalculateWithoutPath() {
        String raw = """
                {"steps":[
                  {"type":"invoke_function","functionName":"calculate","input":{"a":1},"var":"out"},
                  {"type":"return","fields":{"ok":true}}
                ]}
                """;
        String normalized = validator.normalizeAliases(raw);
        assertThatThrownBy(() -> validator.validate(normalized))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("objectPath");
    }
}
