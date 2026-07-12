package com.ispf.server.function;

import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.FunctionDescriptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FunctionDescriptorExpressionTest {

    @Test
    void expressionBodyIsNotScriptBody() {
        DataSchema input = DataSchema.builder("in").field("value", FieldType.DOUBLE).build();
        DataSchema output = DataSchema.builder("out").field("result", FieldType.DOUBLE).build();
        FunctionDescriptor descriptor = new FunctionDescriptor(
                "exprFn",
                "Expression fn",
                input,
                output,
                "expression",
                "input.value * 2",
                null,
                null
        );

        assertThat(descriptor.hasExpressionBody()).isTrue();
        assertThat(descriptor.hasScriptBody()).isFalse();
        assertThat(descriptor.hasJavaBody()).isFalse();
    }

    @Test
    void scriptBodyStillDetectedForScriptType() {
        DataSchema input = DataSchema.builder("in").build();
        DataSchema output = DataSchema.builder("out").field("ok", FieldType.BOOLEAN).build();
        FunctionDescriptor descriptor = new FunctionDescriptor(
                "scriptFn",
                "Script fn",
                input,
                output,
                "script",
                "{\"steps\":[{\"type\":\"return\",\"fields\":{\"ok\":true}}]}",
                null,
                null
        );

        assertThat(descriptor.hasScriptBody()).isTrue();
        assertThat(descriptor.hasExpressionBody()).isFalse();
    }
}
