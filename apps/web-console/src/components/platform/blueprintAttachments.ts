import type { BlueprintAttachmentDto } from "../../types/blueprints";

export function attachmentsForBlueprint(
  attachments: BlueprintAttachmentDto[],
  blueprintId: string
): BlueprintAttachmentDto[] {
  return attachments.filter((row) => row.blueprintId === blueprintId);
}
