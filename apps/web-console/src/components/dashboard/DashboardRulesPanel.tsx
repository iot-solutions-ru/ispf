import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { Typography } from "antd";
import BindingRulesPanel from "../binding/BindingRulesPanel";
import { dashboardRuleTemplates } from "./dashboardRuleTemplates";
import type { DashboardWidget } from "../../types/dashboard";

interface DashboardRulesPanelProps {
  path: string;
  widgets: DashboardWidget[];
  canManage?: boolean;
}

export default function DashboardRulesPanel({
  path,
  widgets,
  canManage = true,
}: DashboardRulesPanelProps) {
  const { t } = useTranslation("dashboard");
  const widgetIds = useMemo(() => widgets.map((widget) => widget.id), [widgets]);
  const templates = useMemo(() => dashboardRuleTemplates(widgetIds), [widgetIds]);

  return (
    <aside className="dashboard-rules-sidebar panel">
      <header className="dashboard-rules-sidebar__header">
        <Typography.Title level={4}>{t("rules.title")}</Typography.Title>
        <Typography.Paragraph type="secondary">{t("rules.hint")}</Typography.Paragraph>
      </header>
      <BindingRulesPanel
        path={path}
        canManage={canManage}
        dashboardMode
        ruleTemplates={templates.map((template) => ({
          id: template.id,
          label: t(template.labelKey),
          rule: template.rule,
        }))}
      />
    </aside>
  );
}
