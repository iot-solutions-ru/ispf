import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { invokeFunction, runWorkflow } from "../../../api";
import { refreshWorkQueue } from "../../../hooks/workQueueCache";
import type { DataRecord } from "../../../types";
import type { FunctionWidget } from "../../../types/dashboard";
import { parseInstanceState } from "../../../types/workflow";
import { parseFunctionInputJson, resolveWidgetPath } from "../dashboardUtils";
import { useDashboardContext } from "../DashboardContext";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";

interface FunctionWidgetViewProps {
  widget: FunctionWidget;
  editable?: boolean;
}

export default function FunctionWidgetView({ widget, editable }: FunctionWidgetViewProps) {
  const { t } = useTranslation("widgets");
  const styles = useWidgetStyles(widget.stylesJson);
  const { selection } = useDashboardContext();
  const queryClient = useQueryClient();
  const objectPath = resolveWidgetPath(widget.objectPath, widget.selectionKey, selection);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const workflowPath = widget.workflowPath?.trim();
  const canRun = workflowPath ? true : Boolean(objectPath && widget.functionName);

  const mutation = useMutation({
    mutationFn: () => {
      if (workflowPath) {
        return runWorkflow(workflowPath, objectPath ?? undefined);
      }
      if (!objectPath || !widget.functionName) {
        throw new Error(t("error.objectAndFunctionRequired"));
      }
      let input: DataRecord | undefined;
      if (widget.inputJson?.trim()) {
        input = parseFunctionInputJson(widget.inputJson);
      }
      return invokeFunction(objectPath, widget.functionName, input);
    },
    onSuccess: (result) => {
      if (workflowPath) {
        const state = parseInstanceState(result.instanceState);
        if (state.status === "FAILED") {
          setError(state.errorMessage ?? t("view.errorGeneric"));
          setMessage(null);
          return;
        }
        const text =
          state.status === "WAITING"
            ? t("view.workflowWaiting")
            : t("view.workflowStarted");
        setMessage(text);
        setError(null);
        void refreshWorkQueue(queryClient);
        queryClient.invalidateQueries({ queryKey: ["variables"] });
        queryClient.invalidateQueries({ queryKey: ["objects"] });
        queryClient.invalidateQueries({ queryKey: ["events"] });
        return;
      }
      const row = result.rows?.[0];
      if (row?.success === false) {
        setError(String(row.message ?? t("view.errorGeneric")));
        setMessage(null);
        return;
      }
      const text = row?.message ? String(row.message) : t("view.done");
      setMessage(text);
      setError(null);
      queryClient.invalidateQueries({ queryKey: ["variables"] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      queryClient.invalidateQueries({ queryKey: ["events"] });
    },
    onError: (err: Error) => {
      setError(err.message);
      setMessage(null);
    },
  });

  const handleClick = () => {
    if (editable) return;
    if (widget.confirmMessage && !window.confirm(widget.confirmMessage)) {
      return;
    }
    mutation.mutate();
  };

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget function-widget"
      editable={editable}
    >
      <button
        type="button"
        className="btn primary function-widget-btn"
        style={styles.value}
        disabled={editable || mutation.isPending || !canRun}
        onClick={handleClick}
      >
        {mutation.isPending
          ? "…"
          : widget.buttonLabel ?? widget.functionName ?? workflowPath ?? t("view.runWorkflow")}
      </button>
      {message && <p className="function-widget-msg ok">{message}</p>}
      {error && <p className="function-widget-msg error">{error}</p>}
    </DashWidgetShell>
  );
}
