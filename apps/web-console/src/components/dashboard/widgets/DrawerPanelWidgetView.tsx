import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import type { DashboardWidget, DrawerPanelWidget } from "../../../types/dashboard";
import DashWidgetShell from "../DashWidgetShell";
import { parseJsonArray } from "../dashboardUtils";
import { useWidgetStyles } from "../widgetStyles";
import { ContainerChildGridOrList } from "../ContainerChildGrid";
import { renderWidgetList } from "../renderDashboardWidget";

interface DrawerPanelWidgetViewProps {
  widget: DrawerPanelWidget;
  refreshIntervalMs: number;
  editable?: boolean;
  depth?: number;
}

export default function DrawerPanelWidgetView({
  widget,
  refreshIntervalMs,
  editable,
  depth = 0,
}: DrawerPanelWidgetViewProps) {
  const { t } = useTranslation("widgets");
  const styles = useWidgetStyles(widget.stylesJson);
  const [open, setOpen] = useState(false);
  const children = useMemo(
    () => parseJsonArray<DashboardWidget>(widget.childrenJson, []),
    [widget.childrenJson]
  );
  const showBody = editable || open;

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-drawer"
      editable={editable}
    >
      {!editable && (
        <button type="button" className="btn" onClick={() => setOpen((v) => !v)}>
          {widget.drawerLabel ?? (open ? t("view.drawerHide") : t("view.drawerOpen"))}
        </button>
      )}
      {showBody && (
        <div className="dash-drawer-body" style={styles.body}>
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
