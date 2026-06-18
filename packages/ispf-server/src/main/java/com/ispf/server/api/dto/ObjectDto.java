package com.ispf.server.api.dto;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.ObjectType;

import java.time.Instant;
import java.util.List;

public record ObjectDto(
        String id,
        String path,
        ObjectType type,
        String displayName,
        String description,
        String templateId,
        Instant createdAt,
        List<String> variableNames,
        List<String> eventNames
) {
    public static ObjectDto from(PlatformObject node) {
        return new ObjectDto(
                node.id(),
                node.path(),
                node.type(),
                node.displayName(),
                node.description(),
                node.templateId().orElse(null),
                node.createdAt(),
                node.variables().keySet().stream().sorted().toList(),
                node.events().keySet().stream().sorted().toList()
        );
    }
}
