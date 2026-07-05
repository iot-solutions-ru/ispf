package com.ispf.server.plugin.blueprint.dto;

import com.ispf.plugin.blueprint.BlueprintApplyResult;
import com.ispf.plugin.blueprint.BlueprintAttachment;
import com.ispf.plugin.blueprint.BlueprintMergeWarning;

import java.time.Instant;
import java.util.List;

public record BlueprintAttachmentDto(
        String id,
        String blueprintId,
        String blueprintName,
        String blueprintType,
        String objectPath,
        Instant attachedAt,
        List<BlueprintMergeWarningDto> warnings
) {
    public static BlueprintAttachmentDto from(BlueprintAttachment attachment) {
        return from(attachment, List.of());
    }

    public static BlueprintAttachmentDto from(BlueprintApplyResult result) {
        return from(result.attachment(), result.warnings());
    }

    public static BlueprintAttachmentDto from(BlueprintAttachment attachment, List<BlueprintMergeWarning> warnings) {
        return new BlueprintAttachmentDto(
                attachment.id(),
                attachment.blueprintId(),
                attachment.blueprintName(),
                attachment.blueprintType().name(),
                attachment.objectPath(),
                attachment.attachedAt(),
                warnings.stream().map(BlueprintMergeWarningDto::from).toList()
        );
    }
}
