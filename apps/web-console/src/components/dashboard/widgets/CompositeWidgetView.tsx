import { useMemo } from "react";
import type { CompositeWidget, DashboardWidget } from "../../../types/dashboard";
import DashboardWidgetContent from "../DashboardWidgetContent";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";

interface CompositeWidgetViewProps {
  widget: CompositeWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function CompositeWidgetView({
  widget,
  refreshIntervalMs,
  editable,
}: CompositeWidgetViewProps) {
  const styles = useWidgetStyles(widget.stylesJson);

  const children = useMemo(() => {
    if (!widget.childrenJson?.trim()) {
      return [] as DashboardWidget[];
    }
    try {
      const parsed = JSON.parse(widget.childrenJson) as DashboardWidget[];
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [] as DashboardWidget[];
    }
  }, [widget.childrenJson]);

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-composite"
      editable={editable}
    >
      {children.length === 0 ? (
        <p className="hint">Укажите childrenJson</p>
      ) : (
        <div className="dash-composite-body" style={styles.body}>
          {children.map((child, index) => (
            <div key={child.id ?? `child-${index}`} className="dash-composite-child">
              <DashboardWidgetContent
                widget={child}
                refreshIntervalMs={refreshIntervalMs}
                editable={editable ?? false}
              />
            </div>
          ))}
        </div>
      )}
    </DashWidgetShell>
  );
}
