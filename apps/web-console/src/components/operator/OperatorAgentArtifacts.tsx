import { useState } from "react";
import { useTranslation } from "react-i18next";
import BffDataTable from "./BffDataTable";
import {
  columnLabels,
  parseOperatorAgentArtifacts,
  type OperatorAgentLink,
  type OperatorAgentTablePreview,
} from "../../utils/operatorAgentArtifacts";

interface OperatorAgentArtifactsViewProps {
  result?: Record<string, unknown>;
  onOpenDashboard?: (path: string) => void;
  onOpenReport?: (path: string) => void;
  onSuggestMessage?: (message: string) => void;
}

function openLink(link: OperatorAgentLink, handlers: OperatorAgentArtifactsViewProps) {
  if (link.kind === "report") {
    if (handlers.onOpenReport) {
      handlers.onOpenReport(link.path);
      return;
    }
  } else if (handlers.onOpenDashboard) {
    handlers.onOpenDashboard(link.path);
    return;
  }
  if (link.url) {
    window.location.assign(link.url);
  }
}

function TablePreviewModal({
  table,
  onClose,
}: {
  table: OperatorAgentTablePreview;
  onClose: () => void;
}) {
  const { t } = useTranslation("operator");
  const title = table.title ?? table.reportPath ?? t("agent.tablePreview");
  return (
    <div className="operator-agent-modal-backdrop" role="presentation" onClick={onClose}>
      <div
        className="operator-agent-modal"
        role="dialog"
        aria-label={title}
        onClick={(e) => e.stopPropagation()}
      >
        <header className="operator-agent-modal-head">
          <div>
            <strong>{title}</strong>
            {table.truncated && <p className="hint">{t("agent.tableTruncated")}</p>}
          </div>
          <button type="button" className="btn small" onClick={onClose} aria-label={t("agent.closeTable")}>
            ×
          </button>
        </header>
        <div className="operator-agent-modal-body">
          <BffDataTable rows={table.rows} labels={columnLabels(table)} />
        </div>
      </div>
    </div>
  );
}

export default function OperatorAgentArtifactsView(props: OperatorAgentArtifactsViewProps) {
  const { t } = useTranslation("operator");
  const parsed = parseOperatorAgentArtifacts(props.result);
  const links = parsed.links ?? [];
  const tables = parsed.tables ?? [];
  const suggestions = parsed.suggestions ?? [];
  const [activeTable, setActiveTable] = useState<OperatorAgentTablePreview | null>(null);

  if (links.length === 0 && tables.length === 0 && suggestions.length === 0) {
    return null;
  }

  return (
    <>
      <div className="operator-agent-artifacts">
        {suggestions.length > 0 && (
          <div className="operator-agent-suggestions-inline">
            <p className="hint">{t("agent.pickOption")}</p>
            <ul>
              {suggestions.map((item) => (
                <li key={`${item.path ?? item.label}:${item.message}`}>
                  <button
                    type="button"
                    className={`btn link${item.primary ? " primary" : ""}`}
                    onClick={() => props.onSuggestMessage?.(item.message)}
                  >
                    {item.label}
                  </button>
                </li>
              ))}
            </ul>
          </div>
        )}
        {links.length > 0 && (
          <div className="operator-agent-artifact-links">
            {links.map((link) => (
              <button
                key={`${link.kind}:${link.path}`}
                type="button"
                className="btn small"
                onClick={() => openLink(link, props)}
              >
                {link.kind === "report" ? t("agent.openReport") : t("agent.openDashboard")}: {link.title}
              </button>
            ))}
          </div>
        )}
        {tables.map((table, index) => (
          <button
            key={`${table.reportPath ?? table.title ?? "table"}-${index}`}
            type="button"
            className="btn small primary"
            onClick={() => setActiveTable(table)}
          >
            {t("agent.openTable", {
              title: table.title ?? table.reportPath ?? t("agent.tablePreview"),
              count: table.rowCount ?? table.rows.length,
            })}
          </button>
        ))}
      </div>
      {activeTable && <TablePreviewModal table={activeTable} onClose={() => setActiveTable(null)} />}
    </>
  );
}
