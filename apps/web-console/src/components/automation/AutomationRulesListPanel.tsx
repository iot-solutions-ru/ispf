import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Table } from "antd";
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
          <Button type="primary" size="small" onClick={() => setShowCreate(true)}>
            {t(isAlertRules ? "catalog.createAlertRule" : "catalog.createCorrelator")}
          </Button>
        )}
      </header>

      {listQuery.isLoading && <p className="hint">{t("common:action.loading")}</p>}
      {listQuery.error && <Alert type="error" showIcon message={String(listQuery.error)} />}

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
    <Table
      className="automation-rules-table"
      size="small"
      rowKey="id"
      dataSource={rules}
      pagination={false}
      columns={[
        {
          title: t("common:table.name"),
          dataIndex: "name",
          render: (value: string, rule) => (
            <Button type="link" onClick={() => onSelectPath(rule.id)}>
              <code>{value}</code>
            </Button>
          ),
        },
        {
          title: t("catalog.column.targetObject"),
          dataIndex: "objectPath",
          className: "mono small",
        },
        {
          title: t("catalog.column.variable"),
          dataIndex: "watchVariable",
          render: (value: string) => <code>{value}</code>,
        },
        {
          title: t("catalog.column.event"),
          dataIndex: "eventName",
          render: (value: string) => <code>{value}</code>,
        },
        {
          title: t("common:table.enabled"),
          dataIndex: "enabled",
          render: (value: boolean) => (value ? t("common:action.yes") : t("common:action.no")),
        },
      ]}
    />
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
    <Table
      className="automation-rules-table"
      size="small"
      rowKey="id"
      dataSource={correlators}
      pagination={false}
      columns={[
        {
          title: t("common:table.name"),
          dataIndex: "name",
          render: (value: string, rule) => (
            <Button type="link" onClick={() => onSelectPath(rule.id)}>
              <code>{value}</code>
            </Button>
          ),
        },
        { title: t("catalog.column.pattern"), dataIndex: "patternType" },
        {
          title: t("catalog.column.event"),
          className: "mono small",
          render: (_, rule) => (
            <>
              <code>{rule.eventName}</code>
              {rule.secondEventName ? ` → ${rule.secondEventName}` : ""}
            </>
          ),
        },
        {
          title: t("catalog.column.action"),
          dataIndex: "actionType",
          className: "mono small",
        },
        {
          title: t("common:table.enabled"),
          dataIndex: "enabled",
          render: (value: boolean) => (value ? t("common:action.yes") : t("common:action.no")),
        },
      ]}
    />
  );
}
