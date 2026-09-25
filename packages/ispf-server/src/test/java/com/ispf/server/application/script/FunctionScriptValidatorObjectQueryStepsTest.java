package com.ispf.server.application.script;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FunctionScriptValidatorObjectQueryStepsTest {

    private final FunctionScriptValidator validator = new FunctionScriptValidator(new ObjectMapper());

    @Test
    void acceptsQueryRowsScanObjectsForEachAndPatchSteps() {
        assertThatCode(() -> validator.validate("""
                {
                  "steps": [
                    {"type": "queryRows", "var": "rows", "spec": "{\\"from\\":{\\"sourcePathPattern\\":\\"root.platform.devices.*\\"}}"},
                    {"type": "scan_objects", "var": "devices", "spec": "{\\"from\\":{\\"sourcePathPattern\\":\\"root.platform.devices.*\\"}}"},
                    {
                      "type": "for_each_row",
                      "source": "rows",
                      "rowVar": "row",
                      "steps": [
                        {"type": "setVar", "var": "path", "expression": "row.path"}
                      ]
                    },
                    {"type": "apply_query_patch", "patches": "[]", "var": "applied"},
                    {"type": "return", "fields": {"ok": true}}
                  ]
                }
                """)).doesNotThrowAnyException();
    }

    @Test
    void rejectsQueryRowsWithoutSpec() {
        assertThatThrownBy(() -> validator.validate("""
                {
                  "steps": [
                    {"type": "queryRows", "var": "rows"},
                    {"type": "return", "fields": {"ok": true}}
                  ]
                }
                """)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires spec");
    }
}
