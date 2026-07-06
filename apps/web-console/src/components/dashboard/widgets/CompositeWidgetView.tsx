import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import type { CompositeWidget, DashboardWidget } from "../../../types/dashboard";
import DashWidgetShell from "../DashWidgetShell";
import { parseJsonArray } from "../dashboardUtils";
import { useWidgetStyles } from "../widgetStyles";
import { ContainerChildGridOrList } from "../ContainerChildGrid";
import { renderWidgetList } from "../renderDashboardWidget";

interface CompositeWidgetViewProps {
  widget: CompositeWidget;
  refreshIntervalMs: number;
  editable?: boolean;
  depth?: number;
}

export default function CompositeWidgetView({
  widget,
  refreshIntervalMs,
  editable,
  depth = 0,
}: CompositeWidgetViewProps) {
  const { t } = useTranslation("widgets");
  const styles = useWidgetStyles(widget.stylesJson);
  const children = useMemo(
    () => parseJsonArray<DashboardWidget>(widget.childrenJson, []),
    [widget.childrenJson]
  );

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-composite"
      editable={editable}
    >
      <div className="dash-composite-body" style={styles.body}>
        <ContainerChildGridOrList
        slotRef={{ kind: "children", containerId: widget.id }}
        children={children}
        bodyClassName="dash-composite-body"
        emptyHint={t("view.specifyChildrenJson")}
        renderList={() =>
          renderWidgetList(children, {
            refreshIntervalMs,
            editable: editable ?? false,
            depth: depth + 1,
          })
        }
      />
      </div>
    </DashWidgetShell>
  );
}
