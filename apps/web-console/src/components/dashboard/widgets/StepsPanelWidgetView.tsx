import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import type { DashboardWidget, StepsPanelWidget } from "../../../types/dashboard";
import { resolveContextParam } from "../dashboardUtils";
import { useWidgetSession } from "../../../hooks/useWidgetObjectPath";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";
import { useDashboardEditor } from "../DashboardEditorContext";
import { ContainerChildGridOrList } from "../ContainerChildGrid";
import { renderWidgetList } from "../renderDashboardWidget";

interface StepDef {
  id: string;
  label: string;
  children: DashboardWidget[];
}

interface StepsPanelWidgetViewProps {
  widget: StepsPanelWidget;
  refreshIntervalMs: number;
  editable?: boolean;
  depth?: number;
}

export default function StepsPanelWidgetView({
  widget,
  refreshIntervalMs,
  editable,
  depth = 0,
}: StepsPanelWidgetViewProps) {
  const { t } = useTranslation("widgets");
  const styles = useWidgetStyles(widget.stylesJson);
  const editor = useDashboardEditor();
  const { params } = useWidgetSession();
  const steps = useMemo(() => {
    try {
      const parsed = widget.stepsJson ? (JSON.parse(widget.stepsJson) as StepDef[]) : [];
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [] as StepDef[];
    }
  }, [widget.stepsJson]);

  const contextStep = widget.activeStepKey
    ? String(resolveContextParam(widget.activeStepKey, params) ?? "")
    : "";
  const [localStep, setLocalStep] = useState(steps[0]?.id ?? "");
  const activeId = editable ? localStep : contextStep || localStep;
  const active = steps.find((s) => s.id === activeId) ?? steps[0];

  useEffect(() => {
    const editorStep = editor?.activeSlots.stepId[widget.id];
    if (editor?.enabled && editorStep && steps.some((step) => step.id === editorStep)) {
      setLocalStep(editorStep);
    }
  }, [editor?.activeSlots.stepId, editor?.enabled, steps, widget.id]);

  const selectStep = (stepId: string) => {
    setLocalStep(stepId);
    if (editor?.enabled) {
      editor.setActiveStep(widget.id, stepId);
    }
  };

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-steps"
      editable={editable}
    >
      <nav className="dash-steps-bar">
        {steps.map((step) => (
          <button
            key={step.id}
            type="button"
            className={`btn small ${step.id === active?.id ? "primary" : ""}`}
            onClick={() => selectStep(step.id)}
          >
            {step.label}
          </button>
        ))}
      </nav>
      <div className="dash-steps-body" style={styles.body}>
        <ContainerChildGridOrList
          slotRef={{ kind: "step", containerId: widget.id, stepId: active?.id ?? steps[0]?.id ?? "step-1" }}
          children={active?.children ?? []}
          emptyHint={t("view.noStepWidgets")}
          renderList={() =>
            active?.children?.length
              ? renderWidgetList(active.children, {
                  refreshIntervalMs,
                  editable: editable ?? false,
                  depth: depth + 1,
                })
              : null
          }
        />
      </div>
    </DashWidgetShell>
  );
}
