import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { setVariable } from "../../../api";
import type { GanttChartWidget } from "../../../types/dashboard";
import { readFieldValue } from "../../../types/dashboard";
import { useBoundVariable } from "../../../hooks/useBoundVariable";
import { useWidgetObjectPath } from "../../../hooks/useWidgetObjectPath";
import { cloneRecord } from "../../../utils/record";
import {
  type GanttRow,
  type GanttViewport,
  GANTT_ZOOM_FACTOR,
  buildGanttTicks,
  computeDataBounds,
  fitGanttViewport,
  formatGanttTick,
  ganttBarLayout,
  ganttViewSpan,
  panGanttViewport,
  patchGanttRowTimes,
  timeAtTrackPixel,
  zoomGanttViewport,
} from "../../../utils/ganttChartView";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";
import { useEditorDemoRows } from "../widgetDemoPreview";

interface GanttChartWidgetViewProps {
  widget: GanttChartWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

type DragMode = "pan" | "move-bar";

interface DragState {
  mode: DragMode;
  pointerId: number;
  rowId?: number;
  originX: number;
  originStart: number;
  originEnd: number;
  viewportStart: number;
  viewportEnd: number;
  trackLeft: number;
  trackWidth: number;
}

export default function GanttChartWidgetView({
  widget,
  refreshIntervalMs,
  editable,
}: GanttChartWidgetViewProps) {
  const { t } = useTranslation("widgets");
  const queryClient = useQueryClient();
  const styles = useWidgetStyles(widget.stylesJson);
  const objectPath = useWidgetObjectPath(widget.objectPath, widget.selectionKey);
  const bodyRef = useRef<HTMLDivElement>(null);
  const axisRef = useRef<HTMLDivElement>(null);
  const dragRef = useRef<DragState | null>(null);
  const pendingRowPatchRef = useRef<{ rowId: number; start: number; end: number } | null>(null);

  const labelField = widget.labelField ?? "name";
  const startField = widget.startField ?? "start";
  const endField = widget.endField ?? "end";
  const interactiveEnabled = widget.interactive !== false && !editable;
  const allowBarDrag = widget.allowBarDrag !== false;

  const { variable, writable } = useBoundVariable(
    objectPath,
    widget.variableName ?? "",
    widget.valueField,
    refreshIntervalMs
  );

  const liveRows = useMemo(() => {
    const list = variable?.value?.rows ?? [];
    return list.map((row, index) => ({
      id: index,
      label: String(readFieldValue(row, labelField) ?? index),
      start: Number(readFieldValue(row, startField) ?? 0),
      end: Number(readFieldValue(row, endField) ?? 1),
    }));
  }, [variable, labelField, startField, endField]);

  const { rows: demoGantt, isDemo: isDemoRaw } = useEditorDemoRows(
    widget,
    [] as Array<{ label: string; start: number; end: number }>,
    editable
  );
  const demoRows =
    isDemoRaw && demoGantt.length > 0
      ? demoGantt.map((row, index) => ({
          id: index,
          label: row.label,
          start: row.start,
          end: row.end,
        }))
      : [];
  const rows: GanttRow[] = demoRows.length > 0 ? demoRows : liveRows;
  const isDemo = demoRows.length > 0;

  const bounds = useMemo(() => computeDataBounds(rows), [rows]);
  const [viewport, setViewport] = useState<GanttViewport | null>(null);
  const [draftRows, setDraftRows] = useState<GanttRow[] | null>(null);

  const displayRows = draftRows ?? rows;
  const activeViewport = viewport ?? fitGanttViewport(bounds.min, bounds.max);
  const ticks = useMemo(() => buildGanttTicks(activeViewport), [activeViewport]);
  const canDragBars = interactiveEnabled && allowBarDrag && writable && !isDemo && Boolean(objectPath);

  useEffect(() => {
    setViewport(null);
    setDraftRows(null);
  }, [bounds.min, bounds.max, rows.length]);

  const persistMutation = useMutation({
    mutationFn: async (patch: { rowId: number; start: number; end: number }) => {
      if (!variable?.value) {
        throw new Error("Variable not loaded");
      }
      const record = patchGanttRowTimes(
        cloneRecord(variable.value),
        patch.rowId,
        startField,
        endField,
        patch.start,
        patch.end
      );
      return setVariable(objectPath, widget.variableName ?? "", record);
    },
    onSuccess: () => {
      pendingRowPatchRef.current = null;
      setDraftRows(null);
      queryClient.invalidateQueries({ queryKey: ["variables", objectPath] });
    },
    onError: () => {
      pendingRowPatchRef.current = null;
      setDraftRows(null);
    },
  });

  const flushRowPatch = useCallback(() => {
    const patch = pendingRowPatchRef.current;
    if (!patch || persistMutation.isPending) return;
    persistMutation.mutate(patch);
  }, [persistMutation]);

  const trackMetrics = useCallback((target: HTMLElement) => {
    const track = target.closest(".dash-gantt-track") as HTMLElement | null;
    const rect = track?.getBoundingClientRect();
    return {
      trackLeft: rect?.left ?? 0,
      trackWidth: rect?.width ?? 0,
    };
  }, []);

  const endDrag = useCallback(() => {
    dragRef.current = null;
    flushRowPatch();
  }, [flushRowPatch]);

  useEffect(() => {
    if (!interactiveEnabled) return;

    const handleMove = (event: PointerEvent) => {
      const drag = dragRef.current;
      if (!drag || event.pointerId !== drag.pointerId) return;

      if (drag.mode === "pan") {
        const span = ganttViewSpan({ start: drag.viewportStart, end: drag.viewportEnd });
        const deltaPx = event.clientX - drag.originX;
        const deltaTime = drag.trackWidth > 0 ? (-deltaPx / drag.trackWidth) * span : 0;
        setViewport(
          panGanttViewport(
            { start: drag.viewportStart, end: drag.viewportEnd },
            deltaTime,
            bounds.min,
            bounds.max
          )
        );
        return;
      }

      const span = ganttViewSpan({ start: drag.viewportStart, end: drag.viewportEnd });
      const deltaPx = event.clientX - drag.originX;
      const deltaTime = drag.trackWidth > 0 ? (deltaPx / drag.trackWidth) * span : 0;
      const duration = drag.originEnd - drag.originStart;
      const nextStart = drag.originStart + deltaTime;
      const nextEnd = nextStart + duration;
      const rowId = drag.rowId;
      if (rowId == null) return;

      setDraftRows((current) => {
        const base = current ?? rows;
        return base.map((row) =>
          row.id === rowId ? { ...row, start: nextStart, end: nextEnd } : row
        );
      });
      pendingRowPatchRef.current = { rowId, start: nextStart, end: nextEnd };
    };

    const handleUp = (event: PointerEvent) => {
      if (dragRef.current?.pointerId === event.pointerId) {
        endDrag();
      }
    };

    window.addEventListener("pointermove", handleMove);
    window.addEventListener("pointerup", handleUp);
    window.addEventListener("pointercancel", handleUp);
    return () => {
      window.removeEventListener("pointermove", handleMove);
      window.removeEventListener("pointerup", handleUp);
      window.removeEventListener("pointercancel", handleUp);
    };
  }, [bounds.max, bounds.min, endDrag, interactiveEnabled, rows]);

  const handleWheel = (event: React.WheelEvent<HTMLDivElement>) => {
    if (!interactiveEnabled) return;
    event.preventDefault();
    const axis = axisRef.current;
    if (!axis) return;
    const rect = axis.getBoundingClientRect();
    const anchor = timeAtTrackPixel(event.clientX, rect.left, rect.width, activeViewport);
    const factor = event.deltaY < 0 ? GANTT_ZOOM_FACTOR : 1 / GANTT_ZOOM_FACTOR;
    setViewport(zoomGanttViewport(activeViewport, factor, anchor, bounds.min, bounds.max));
  };

  const handleTrackPointerDown = (event: React.PointerEvent<HTMLDivElement>) => {
    if (!interactiveEnabled || event.button !== 0) return;
    if ((event.target as HTMLElement).closest(".dash-gantt-bar")) return;
    const { trackLeft, trackWidth } = trackMetrics(event.currentTarget);
    dragRef.current = {
      mode: "pan",
      pointerId: event.pointerId,
      originX: event.clientX,
      originStart: 0,
      originEnd: 0,
      viewportStart: activeViewport.start,
      viewportEnd: activeViewport.end,
      trackLeft,
      trackWidth,
    };
    event.currentTarget.setPointerCapture(event.pointerId);
  };

  const handleBarPointerDown = (
    event: React.PointerEvent<HTMLDivElement>,
    row: GanttRow
  ) => {
    if (!canDragBars || event.button !== 0) return;
    event.stopPropagation();
    const { trackLeft, trackWidth } = trackMetrics(event.currentTarget);
    dragRef.current = {
      mode: "move-bar",
      pointerId: event.pointerId,
      rowId: row.id,
      originX: event.clientX,
      originStart: row.start,
      originEnd: row.end,
      viewportStart: activeViewport.start,
      viewportEnd: activeViewport.end,
      trackLeft,
      trackWidth,
    };
    event.currentTarget.setPointerCapture(event.pointerId);
  };

  const handleResetViewport = () => {
    setViewport(null);
    setDraftRows(null);
  };

  const footer = rows.length
    ? interactiveEnabled
      ? canDragBars
        ? t("view.ganttHintInteractiveDrag")
        : t("view.ganttHintInteractive")
      : undefined
    : undefined;

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-gantt"
      editable={editable}
      demo={isDemo}
      footer={footer}
    >
      <div
        ref={bodyRef}
        className={`dash-gantt-body${interactiveEnabled ? " dash-gantt-body--interactive" : ""}`}
        style={styles.body}
        onWheel={handleWheel}
        onDoubleClick={interactiveEnabled ? handleResetViewport : undefined}
        aria-label={t("view.ganttCanvas")}
      >
        {rows.length === 0 ? (
          <p className="hint">{t("view.noGanttRows")}</p>
        ) : (
          <>
            <div className="dash-gantt-axis" aria-hidden="true" ref={axisRef}>
              {ticks.map((tick) => {
                const leftPct =
                  ((tick - activeViewport.start) / ganttViewSpan(activeViewport)) * 100;
                return (
                  <span
                    key={tick}
                    className="dash-gantt-axis-tick"
                    style={{ left: `${leftPct}%` }}
                  >
                    {formatGanttTick(tick)}
                  </span>
                );
              })}
            </div>
            {displayRows.map((row) => {
              const layout = ganttBarLayout(row, activeViewport);
              return (
                <div key={row.id} className="dash-gantt-row">
                  <span className="dash-gantt-label" title={row.label}>
                    {row.label}
                  </span>
                  <div
                    className="dash-gantt-track"
                    onPointerDown={handleTrackPointerDown}
                  >
                    {layout.visible ? (
                      <div
                        className={`dash-gantt-bar${canDragBars ? " dash-gantt-bar--draggable" : ""}`}
                        style={{
                          left: `${layout.leftPct}%`,
                          width: `${layout.widthPct}%`,
                        }}
                        onPointerDown={(event) => handleBarPointerDown(event, row)}
                        title={`${formatGanttTick(row.start)} – ${formatGanttTick(row.end)}`}
                      />
                    ) : null}
                  </div>
                </div>
              );
            })}
          </>
        )}
      </div>
    </DashWidgetShell>
  );
}
