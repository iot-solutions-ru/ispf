import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { fetchAlertRules, fetchCorrelators } from "../../api";
import type { AlertRule, EventCorrelator } from "../../types/event";
import CreateAlertRuleDialog from "./CreateAlertRuleDialog";
import CreateCorrelatorDialog from "./CreateCorrelatorDialog";

type AutomationListKind = "alert-rules" | "correlators";

interface AutomationRulesListPanelProps {
  kind: AutomationListKind;
  canManage: boolean;
  onSelectPath: (path: string) => void;
}

export default function AutomationRulesListPanel({
  kind,
  canManage,
  onSelectPath,
}: AutomationRulesListPanelProps) {
  const { t } = useTranslation(["automation", "common"]);
  const queryClient = useQueryClient();
  const [showCreate, setShowCreate] = useState(false);

  const isAlertRules = kind === "alert-rules";

  const alertRulesQuery = useQuery({
    queryKey: ["alert-rules"],
    queryFn: fetchAlertRules,
    enabled: isAlertRules,
  });
  const correlatorsQuery = useQuery({
    queryKey: ["correlators"],
    queryFn: fetchCorrelators,
    enabled: !isAlertRules,
  });
  const listQuery = isAlertRules ? alertRulesQuery : correlatorsQuery;

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: [isAlertRules ? "alert-rules" : "correlators"] });
    queryClient.invalidateQueries({ queryKey: ["objects"] });
  };

  return (
    <section className="automation-rules-list-panel">
      <header className="automation-panel-head">
        <div>
          <h3>{t(isAlertRules ? "catalog.alertRulesTitle" : "catalog.correlatorsTitle")}</h3>
          <p className="hint">{t(isAlertRules ? "catalog.alertRulesSubtitle" : "catalog.correlatorsSubtitle")}</p>
        </div>
        {canManage && (
          <button type="button" className="btn primary small" onClick={() => setShowCreate(true)}>
            {t(isAlertRules ? "catalog.createAlertRule" : "catalog.createCorrelator")}
          </button>
        )}
      </header>

      {listQuery.isLoading && <p className="hint">{t("common:action.loading")}</p>}
      {listQuery.error && <div className="op-alert op-alert-error">{String(listQuery.error)}</div>}

      {!listQuery.isLoading && !listQuery.error && (listQuery.data?.length ?? 0) === 0 && (
        <p className="hint">{t(isAlertRules ? "catalog.alertRulesEmpty" : "catalog.correlatorsEmpty")}</p>
      )}

      {isAlertRules ? (
        <AlertRulesTable
          rules={(listQuery.data as AlertRule[] | undefined) ?? []}
          onSelectPath={onSelectPath}
        />
      ) : (
        <CorrelatorsTable
          correlators={(listQuery.data as EventCorrelator[] | undefined) ?? []}
          onSelectPath={onSelectPath}
        />
      )}

      {showCreate && isAlertRules && (
        <CreateAlertRuleDialog
          onClose={() => setShowCreate(false)}
          onCreated={() => {
            setShowCreate(false);
            refresh();
          }}
        />
      )}
      {showCreate && !isAlertRules && (
        <CreateCorrelatorDialog
          onClose={() => setShowCreate(false)}
          onCreated={() => {
            setShowCreate(false);
            refresh();
          }}
        />
      )}
    </section>
  );
}

function AlertRulesTable({
  rules,
  onSelectPath,
}: {
  rules: AlertRule[];
  onSelectPath: (path: string) => void;
}) {
  const { t } = useTranslation(["automation", "common"]);

  if (rules.length === 0) {
    return null;
  }

  return (
    <div className="table-scroll">
      <table className="data-table automation-rules-table">
        <thead>
          <tr>
            <th>{t("common:table.name")}</th>
            <th>{t("catalog.column.targetObject")}</th>
            <th>{t("catalog.column.variable")}</th>
            <th>{t("catalog.column.event")}</th>
            <th>{t("common:table.enabled")}</th>
          </tr>
        </thead>
        <tbody>
          {rules.map((rule) => (
            <tr key={rule.id}>
              <td>
                <button type="button" className="link-btn" onClick={() => onSelectPath(rule.id)}>
                  <code>{rule.name}</code>
                </button>
              </td>
              <td className="mono small">{rule.objectPath}</td>
              <td><code>{rule.watchVariable}</code></td>
              <td><code>{rule.eventName}</code></td>
              <td>{rule.enabled ? t("common:action.yes") : t("common:action.no")}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function CorrelatorsTable({
  correlators,
  onSelectPath,
}: {
  correlators: EventCorrelator[];
  onSelectPath: (path: string) => void;
}) {
  const { t } = useTranslation(["automation", "common"]);

  if (correlators.length === 0) {
    return null;
  }

  return (
    <div className="table-scroll">
      <table className="data-table automation-rules-table">
        <thead>
          <tr>
            <th>{t("common:table.name")}</th>
            <th>{t("catalog.column.pattern")}</th>
            <th>{t("catalog.column.event")}</th>
            <th>{t("catalog.column.action")}</th>
            <th>{t("common:table.enabled")}</th>
          </tr>
        </thead>
        <tbody>
          {correlators.map((rule) => (
            <tr key={rule.id}>
              <td>
                <button type="button" className="link-btn" onClick={() => onSelectPath(rule.id)}>
                  <code>{rule.name}</code>
                </button>
              </td>
              <td>{rule.patternType}</td>
              <td className="mono small">
                <code>{rule.eventName}</code>
                {rule.secondEventName ? ` → ${rule.secondEventName}` : ""}
              </td>
              <td className="mono small">{rule.actionType}</td>
              <td>{rule.enabled ? t("common:action.yes") : t("common:action.no")}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
