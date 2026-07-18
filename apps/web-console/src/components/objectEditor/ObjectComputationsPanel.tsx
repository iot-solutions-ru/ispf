import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import type { AnalyticsTagCatalogEntryDto } from "../../types/analytics";
import type { BindingRule, VariableDto } from "../../types";
import AnalyticsTagInspectorModal from "../analytics/AnalyticsTagInspectorModal";
import BindingInvokeJournalPanel from "../runtime/BindingInvokeJournalPanel";
import BindingRulesPanel from "../binding/BindingRulesPanel";
import { defaultBindingActivators } from "../binding/bindingActivatorsUtils";

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

  const ruleTemplates = useMemo(() => {
    const targetVar = variableNames.find((n) => n !== "@bindingRules") ?? "";
    const remoteRead: BindingRule = {
      id: "remote_read",
      name: "remote_read",
      enabled: true,
      order: 0,
      kind: "reactive",
      activators: {
        ...defaultBindingActivators(),
        onStartup: true,
        onVariableChange: [
          {
            objectPath: "root.platform.devices.example",
            variableName: "sineWave",
          },
        ],
      },
      condition: "",
      expression: 'read("root.platform.devices.example/sineWave")',
      target: { kind: "variable", variableName: targetVar, field: "value" },
    };
    const localExpr: BindingRule = {
      id: "local_expr",
      name: "local_expr",
      enabled: true,
      order: 10,
      kind: "reactive",
      activators: {
        ...defaultBindingActivators(),
        onStartup: true,
        onVariableChange: targetVar
          ? [{ objectPath: path, variableName: targetVar }]
          : [],
      },
      condition: "",
      expression: targetVar
        ? `self.${targetVar}["value"] > 0`
        : 'self.member1Sine["value"] > 0 && self.member2Sine["value"] > 0',
      target: { kind: "variable", variableName: targetVar || "clusterError", field: "value" },
    };
    return [
      { id: "remote-read", label: t("inspector:bindings.templates.remoteRead"), rule: remoteRead },
      { id: "local-expr", label: t("inspector:bindings.templates.localExpression"), rule: localExpr },
    ];
  }, [path, t, variableNames]);

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
          ruleTemplates={ruleTemplates}
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
