import { useState } from "react";
import { useTranslation } from "react-i18next";
import type { AnalyticsTagCatalogEntryDto } from "../types/analytics";
import type { VariableDto } from "../types";
import AnalyticsTagInspectorModal from "./analytics/AnalyticsTagInspectorModal";
import BindingInvokeJournalPanel from "./runtime/BindingInvokeJournalPanel";
import BindingRulesPanel from "./BindingRulesPanel";

interface ObjectComputationsPanelProps {
  path: string;
  canManage: boolean;
  eventNames: string[];
  variableNames: string[];
  variables?: VariableDto[];
  functionNames: string[];
  objectType?: string;
  historianComputations?: AnalyticsTagCatalogEntryDto[];
  bindingAuditEnabled?: boolean;
  federated?: boolean;
  revision: number;
  onBindingAuditChange: (enabled: boolean) => void;
}

export default function ObjectComputationsPanel({
  path,
  canManage,
  eventNames,
  variableNames,
  variables,
  functionNames,
  historianComputations = [],
  bindingAuditEnabled = false,
  federated = false,
  revision: _revision,
  onBindingAuditChange,
}: ObjectComputationsPanelProps) {
  const { t } = useTranslation(["inspector", "common"]);
  const [inspectTagPath, setInspectTagPath] = useState<string | null>(null);

  return (
    <>
      <p className="hint computations-intro">{t("inspector:computations.intro")}</p>

      <section className="computations-section computations-rules">
        <header className="computations-section-head">
          <h4>{t("inspector:computations.rulesTitle")}</h4>
          <p className="hint">{t("inspector:computations.rulesHint")}</p>
        </header>
        <BindingRulesPanel
          path={path}
          canManage={canManage}
          eventNames={eventNames}
          variableNames={variableNames}
          variables={variables}
          functionNames={functionNames}
          embedded
          onInspectHistorian={(tagPath) => setInspectTagPath(tagPath)}
        />
      </section>

      {historianComputations.length > 0 && (
        <section className="computations-section computations-historian-status">
          <header className="computations-section-head">
            <h4>{t("inspector:computations.historianStatusTitle")}</h4>
          </header>
          <ul className="computations-historian-list">
            {historianComputations.map((tag) => (
              <li key={tag.path} className="computations-historian-item">
                <strong>{tag.outputVariable}</strong>
                <span className="hint mono">{tag.expression}</span>
                <span className={`status-badge quality-${tag.qualityStatus}`}>{tag.qualityStatus}</span>
                <button
                  type="button"
                  className="btn tiny"
                  onClick={() => setInspectTagPath(tag.path)}
                >
                  {t("inspector:computations.inspect")}
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}

      {canManage && !federated && (
        <label className="binding-audit-toggle panel-toolbar">
          <input
            type="checkbox"
            checked={bindingAuditEnabled}
            onChange={async (e) => {
              await onBindingAuditChange(e.target.checked);
            }}
          />
          {t("inspector:bindings.auditEnabled")}
        </label>
      )}
      <BindingInvokeJournalPanel objectPath={path} compact scrollMaxHeight={360} />

      <AnalyticsTagInspectorModal
        open={inspectTagPath !== null}
        tagPath={inspectTagPath}
        onClose={() => setInspectTagPath(null)}
      />
    </>
  );
}
