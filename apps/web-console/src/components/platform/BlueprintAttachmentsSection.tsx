import { useTranslation } from "react-i18next";
import type { BlueprintAttachmentDto } from "../../types/blueprints";

export function attachmentsForBlueprint(
  attachments: BlueprintAttachmentDto[],
  blueprintId: string
): BlueprintAttachmentDto[] {
  return attachments.filter((row) => row.blueprintId === blueprintId);
}

export default function BlueprintAttachmentsSection({
  attachments,
  loading = false,
  onSelectPath,
}: {
  attachments: BlueprintAttachmentDto[];
  loading?: boolean;
  onSelectPath?: (path: string) => void;
}) {
  const { t } = useTranslation("inspector");

  return (
    <div className="model-action-block" data-testid="blueprint-attachments">
      <p className="hint">{t("blueprint.attachmentsHint")}</p>
      <h5 className="field-label">{t("blueprint.attachmentsTitle")}</h5>
      {loading && <p className="hint">{t("blueprint.attachmentsLoading")}</p>}
      {!loading && attachments.length === 0 && (
        <p className="hint">{t("blueprint.attachmentsEmpty")}</p>
      )}
      {!loading && attachments.length > 0 && (
        <ul className="model-instance-list">
          {attachments.map((row) => (
            <li key={row.id}>
              {onSelectPath ? (
                <button
                  type="button"
                  className="link-btn"
                  onClick={() => onSelectPath(row.objectPath)}
                  title={row.objectPath}
                >
                  <code>{row.objectPath}</code>
                </button>
              ) : (
                <code>{row.objectPath}</code>
              )}
              <span className="hint">
                {" "}
                ({row.blueprintType}
                {row.attachedAt
                  ? ` · ${t("blueprint.attachedAt", { at: row.attachedAt })}`
                  : ""}
                )
              </span>
              {(row.warnings?.length ?? 0) > 0 && (
                <ul className="hint">
                  {row.warnings!.map((warning) => (
                    <li key={`${warning.kind}-${warning.name}`}>
                      {warning.kind}: <code>{warning.name}</code>
                    </li>
                  ))}
                </ul>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
