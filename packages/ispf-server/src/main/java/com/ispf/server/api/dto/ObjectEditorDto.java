package com.ispf.server.api.dto;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.FunctionDescriptor;

import java.util.List;

/**
 * Universal object editor payload — all editable aspects of an object in one response.
 */
public record ObjectEditorDto(
        ObjectDto object,
        List<VariableDto> variables,
        List<EventDescriptor> events,
        List<FunctionDescriptor> functions
) {
    public static ObjectEditorDto from(PlatformObject node) {
        return new ObjectEditorDto(
                ObjectDto.from(node),
                node.variables().values().stream().map(VariableDto::from).sorted(
                        (a, b) -> a.name().compareTo(b.name())
                ).toList(),
                node.events().values().stream().sorted(
                        (a, b) -> a.name().compareTo(b.name())
                ).toList(),
                node.functions().values().stream().sorted(
                        (a, b) -> a.name().compareTo(b.name())
                ).toList()
        );
    }
}
