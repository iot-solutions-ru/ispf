import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import type { PanelWidget, DashboardWidget } from "../../../types/dashboard";
import DashWidgetShell from "../DashWidgetShell";
import { parseJsonArray } from "../dashboardUtils";
import { useWidgetStyles } from "../widgetStyles";
import { ContainerChildGridOrList } from "../ContainerChildGrid";
import { renderWidgetList } from "../renderDashboardWidget";

interface PanelWidgetViewProps {
  widget: PanelWidget;
  refreshIntervalMs: number;
  editable?: boolean;
  depth?: number;
}

export default function PanelWidgetView({
  widget,
  refreshIntervalMs,
  editable,
  depth = 0,
}: PanelWidgetViewProps) {
  const { t } = useTranslation("widgets");
  const styles = useWidgetStyles(widget.stylesJson);
  const [collapsed, setCollapsed] = useState(false);
  const children = useMemo(
    () => parseJsonArray<DashboardWidget>(widget.childrenJson, []),
    [widget.childrenJson]
  );
  const showBody = editable || !collapsed;

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-panel"
      editable={editable}
      headerExtra={
        widget.collapsible && !editable ? (
          <button
            type="button"
            className="btn small"
            onClick={() => setCollapsed((v) => !v)}
          >
            {collapsed ? "▸" : "▾"}
          </button>
        ) : undefined
      }
    >
      {showBody && (
        <div className="dash-panel-body" style={styles.body}>
          <ContainerChildGridOrList
            slotRef={{ kind: "children", containerId: widget.id }}
            children={children}
            emptyHint={t("view.addChildrenJson")}
            renderList={() =>
              renderWidgetList(children, {
                refreshIntervalMs,
                editable: editable ?? false,
                depth: depth + 1,
              })
            }
          />
        </div>
      )}
    </DashWidgetShell>
  );
}
