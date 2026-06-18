package com.ispf.core.template;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.model.DataSchema;

import java.util.List;

/**
 * A reusable blueprint for creating object instances.
 */
public record ObjectTemplate(
        String id,
        String name,
        String description,
        ObjectType targetType,
        List<VariableTemplate> variables,
        List<FunctionDescriptor> functions,
        List<EventDescriptor> events,
        List<BindingTemplate> bindings
) {
    public record VariableTemplate(
            String name,
            DataSchema schema,
            boolean readable,
            boolean writable,
            String defaultBinding
    ) {
    }

    public record BindingTemplate(
            String targetVariable,
            String expression
    ) {
    }
}
