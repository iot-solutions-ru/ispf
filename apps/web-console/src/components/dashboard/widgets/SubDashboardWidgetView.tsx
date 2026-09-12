import { useMemo, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { fetchDashboard } from "../../../api";
import type { DashboardLayout, SubDashboardWidget } from "../../../types/dashboard";
import { resolveDashboardLayout } from "../../../types/dashboard";
import DashWidgetShell from "../DashWidgetShell";
import { resolveContextPath } from "../dashboardUtils";
import { DashboardProvider, useDashboardContext } from "../DashboardContext";
import { useWidgetSession } from "../../../hooks/useWidgetObjectPath";
import { useWidgetStyles } from "../widgetStyles";
import DashboardGrid from "../DashboardGrid";
import { subDashboardSharesParentContext } from "./subDashboardContext";

const MAX_SUB_DASHBOARD_DEPTH = 2;

interface SubDashboardWidgetViewProps {
  widget: SubDashboardWidget;
  refreshIntervalMs: number;
  editable?: boolean;
  depth?: number;
}

export default function SubDashboardWidgetView({
  widget,
  refreshIntervalMs,
  editable,
  depth = 0,
}: SubDashboardWidgetViewProps) {
  const { t } = useTranslation(["widgets", "common"]);
  const styles = useWidgetStyles(widget.stylesJson);
  const session = useWidgetSession();

  const resolvedPath = useMemo(
    () =>
      resolveContextPath(
        widget.targetDashboardPath,
        widget.targetDashboardPathKey,
        session
      ),
    [
      widget.targetDashboardPath,
      widget.targetDashboardPathKey,
      session.selection,
      session.params,
    ]
  );

  const embedded = useQuery({
    queryKey: ["dashboard", resolvedPath],
    queryFn: () => fetchDashboard(resolvedPath),
    enabled: Boolean(resolvedPath),
  });

  const layout = useMemo(
    () => resolveDashboardLayout(embedded.data),
    [embedded.data]
  );

  const nestedRefresh = embedded.data?.refreshIntervalMs ?? refreshIntervalMs;

  if (!resolvedPath) {
    return (
      <DashWidgetShell
        title={widget.title}
        stylesJson={widget.stylesJson}
        className="dash-widget dash-widget-sub-dashboard"
        editable={editable}
      >
        <p className="hint">{t("view.specifyTargetDashboardOrKey")}</p>
      </DashWidgetShell>
    );
  }

  if (depth >= MAX_SUB_DASHBOARD_DEPTH) {
    return (
      <DashWidgetShell title={widget.title} className="dash-widget" editable={editable}>
        <p className="hint">{t("view.subDashboardMaxDepth")}</p>
      </DashWidgetShell>
    );
  }

  return (
    <DashWidgetShell
      title={widget.title || embedded.data?.title || resolvedPath}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-sub-dashboard"
      editable={editable}
      footer={resolvedPath}
    >
      <div className="dash-sub-dashboard-body" style={styles.body}>
        {embedded.isLoading ? (
          <p className="hint">{t("common:action.loading")}</p>
        ) : embedded.error ? (
          <p className="hint">{t("view.subDashboardLoadError")}</p>
        ) : (
          <NestedSubDashboardGrid
            widget={widget}
            layout={layout}
            refreshIntervalMs={nestedRefresh}
            depth={depth}
          />
        )}
      </div>
    </DashWidgetShell>
  );
}

function NestedSubDashboardGrid({
  widget,
  layout,
  refreshIntervalMs,
  depth,
}: {
  widget: SubDashboardWidget;
  layout: DashboardLayout;
  refreshIntervalMs: number;
  depth: number;
}): ReactNode {
  const parent = useDashboardContext();
  const grid = (
    <DashboardGrid
      layout={layout}
      refreshIntervalMs={refreshIntervalMs}
      editable={false}
      subDashboardDepth={depth + 1}
    />
  );
  if (subDashboardSharesParentContext(widget.inheritContext)) {
    return grid;
  }
  return (
    <DashboardProvider
      operatorMode={parent.operatorMode}
      embeddedModal={parent.embeddedModal}
      closeDashboardModal={parent.closeDashboardModal}
      onNavigateDashboard={parent.navigateToDashboard}
      onOpenDashboardModal={parent.openDashboardModal}
    >
      {grid}
    </DashboardProvider>
  );
}
