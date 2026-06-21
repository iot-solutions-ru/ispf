package com.ispf.server.api.dto;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.server.federation.FederationProxyMetadata;
import com.ispf.server.object.BindingStateVariables;
import com.ispf.server.object.ObjectUiIconService;

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
        return from(node, null);
    }

    public static ObjectEditorDto from(PlatformObject node, ObjectUiIconService iconService) {
        String iconId = iconService != null
                ? iconService.readIconId(node).orElse(null)
                : null;
        return new ObjectEditorDto(
                ObjectDto.from(node, iconId),
                node.variables().values().stream()
                        .filter(v -> !ObjectUiIconService.UI_ICON_VARIABLE.equals(v.name()))
                        .filter(v -> !BindingStateVariables.isReserved(v.name()))
                        .filter(v -> !FederationProxyMetadata.isFederationVariable(v.name()))
                        .map(VariableDto::from)
                        .sorted((a, b) -> a.name().compareTo(b.name()))
                        .toList(),
                node.events().values().stream().sorted(
                        (a, b) -> a.name().compareTo(b.name())
                ).toList(),
                node.functions().values().stream().sorted(
                        (a, b) -> a.name().compareTo(b.name())
                ).toList()
        );
    }
}
