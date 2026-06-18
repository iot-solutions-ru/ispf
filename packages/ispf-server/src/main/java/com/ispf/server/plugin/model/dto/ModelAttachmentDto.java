package com.ispf.server.plugin.model.dto;

import com.ispf.plugin.model.ModelAttachment;

import java.time.Instant;

public record ModelAttachmentDto(
        String id,
        String modelId,
        String modelName,
        String modelType,
        String objectPath,
        Instant attachedAt
) {
    public static ModelAttachmentDto from(ModelAttachment attachment) {
        return new ModelAttachmentDto(
                attachment.id(),
                attachment.modelId(),
                attachment.modelName(),
                attachment.modelType().name(),
                attachment.objectPath(),
                attachment.attachedAt()
        );
    }
}
