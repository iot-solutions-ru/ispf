import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { invokeFunction, setVariable } from "../../../api";
import type { SvgWidget } from "../../../types/dashboard";
import { resolveWidgetPath } from "../dashboardUtils";
import { useDashboardContext } from "../DashboardContext";
import { useBoundVariable } from "../../../hooks/useBoundVariable";
import { useWidgetObjectPath } from "../../../hooks/useWidgetObjectPath";
import { cloneRecord, setFieldValue } from "../../../utils/record";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";
import { resolveWidgetMediaSrc } from "../widgetMediaUrl";
import { resolveSvgInteractiveConfig } from "../../../scada/svgWidgetInteractive";
import SvgInteractiveBody from "./SvgInteractiveBody";

interface SvgWidgetViewProps {
  widget: SvgWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function SvgWidgetView({
  widget,
  refreshIntervalMs,
  editable,
}: SvgWidgetViewProps) {
  const { t } = useTranslation("widgets");
  const styles = useWidgetStyles(widget.stylesJson);
  const { selection } = useDashboardContext();
  const queryClient = useQueryClient();
  const objectPath = useWidgetObjectPath(widget.objectPath, widget.selectionKey);
  const functionPath = resolveWidgetPath(widget.objectPath, widget.selectionKey, selection);
  const togglePath = objectPath;
  const toggleVariable = widget.toggleVariable ?? widget.variableName ?? "";
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const interactiveConfig = useMemo(() => resolveSvgInteractiveConfig(widget), [widget]);
  const topologyMode = interactiveConfig !== null;

  const { rawValue, variable, writable } = useBoundVariable(
    togglePath,
    toggleVariable,
    widget.valueField,
    topologyMode ? false : refreshIntervalMs
  );

  const functionMutation = useMutation({
    mutationFn: () => {
      if (!functionPath || !widget.functionName) {
        throw new Error(t("error.objectAndFunctionRequired"));
      }
      return invokeFunction(functionPath, widget.functionName);
    },
    onSuccess: () => {
      setMessage(t("view.done"));
      setError(null);
      queryClient.invalidateQueries({ queryKey: ["variables"] });
      queryClient.invalidateQueries({ queryKey: ["events"] });
    },
    onError: (err: Error) => {
      setError(err.message);
      setMessage(null);
    },
  });

  const toggleMutation = useMutation({
    mutationFn: async () => {
      if (!variable?.value) {
        throw new Error("Variable not loaded");
      }
      const active = rawValue === true || rawValue === "true" || rawValue === 1;
      const field = widget.valueField ?? "value";
      const record = setFieldValue(cloneRecord(variable.value), field, !active);
      return setVariable(togglePath, toggleVariable, record);
    },
    onSuccess: () => {
      setMessage(null);
      setError(null);
      queryClient.invalidateQueries({ queryKey: ["variables", togglePath] });
    },
    onError: (err: Error) => {
      setError(err.message);
    },
  });

  const clickable =
    !topologyMode &&
    !editable &&
    (widget.clickAction === "function"
      ? Boolean(functionPath && widget.functionName)
      : widget.clickAction === "toggle"
        ? Boolean(togglePath && toggleVariable && writable)
        : false);

  const handleClick = () => {
    if (!clickable) {
      return;
    }
    if (widget.confirmMessage && !window.confirm(widget.confirmMessage)) {
      return;
    }
    if (widget.clickAction === "function") {
      functionMutation.mutate();
      return;
    }
    if (widget.clickAction === "toggle") {
      toggleMutation.mutate();
    }
  };

  const svgSrc = resolveWidgetMediaSrc(widget.svgUrl);

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className={`dash-widget dash-widget-svg${topologyMode ? " dash-widget-svg-interactive" : ""}`}
      editable={editable}
    >
      {topologyMode && interactiveConfig ? (
        <SvgInteractiveBody
          svgUrl={widget.svgUrl}
          config={interactiveConfig}
          selectionKey={widget.selectionKey}
          hitTargetDashboard={widget.hitTargetDashboard}
          hitOpenMode={widget.hitOpenMode}
          showLegend={widget.showLegend}
          panEnabled={widget.panEnabled}
          defaultZoom={widget.defaultZoom}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
          title={typeof widget.title === "string" ? widget.title : undefined}
        />
      ) : !svgSrc ? (
        <p className="hint">{t("view.specifySvgUrl")}</p>
      ) : (
        <button
          type="button"
          className={`dash-svg-widget-btn${clickable ? " clickable" : ""}`}
          style={styles.body}
          disabled={!clickable || functionMutation.isPending || toggleMutation.isPending}
          onClick={handleClick}
        >
          <img src={svgSrc} alt={widget.title} className="dash-svg-widget-image" />
        </button>
      )}
      {message && <p className="function-widget-msg ok">{message}</p>}
      {error && <p className="function-widget-msg error">{error}</p>}
    </DashWidgetShell>
  );
}
