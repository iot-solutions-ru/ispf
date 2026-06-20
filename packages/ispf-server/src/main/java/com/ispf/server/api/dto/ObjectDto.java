package com.ispf.server.api.dto;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.ObjectType;
import com.ispf.server.object.ObjectUiIconService;

import java.time.Instant;
import java.util.List;

public record ObjectDto(
        String id,
        String path,
        ObjectType type,
        String displayName,
        String description,
        String templateId,
        String iconId,
        Instant createdAt,
        List<String> variableNames,
        List<String> eventNames
) {
    public static ObjectDto from(PlatformObject node) {
        return from(node, null);
    }

    public static ObjectDto from(PlatformObject node, String iconId) {
        return new ObjectDto(
                node.id(),
                node.path(),
                node.type(),
                node.displayName(),
                node.description(),
                node.templateId().orElse(null),
                iconId,
                node.createdAt(),
                node.variables().keySet().stream()
                        .filter(name -> !ObjectUiIconService.UI_ICON_VARIABLE.equals(name))
                        .sorted()
                        .toList(),
                node.events().keySet().stream().sorted().toList()
        );
    }
}
