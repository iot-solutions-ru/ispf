import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchMimic, invokeFunction, setVariable } from "../../../api";
import type { ScadaMimicWidget } from "../../../types/dashboard";
import type { MimicAction, MimicElement, ScadaMimicDocument } from "../../../types/scadaMimic";
import { parseMimicDocument } from "../../../scada/document";
import { parseSelectionJson } from "../dashboardUtils";
import {
  collectBindingPaths,
  resolveBindingValue,
  resolveDocumentBindings,
} from "../../../scada/bindingResolver";
import {
  applyCycleUnit,
  applyToggleExpand,
  applyToggleLayer,
  buildElementTooltip,
  contextActions,
  primaryAction,
} from "../../../scada/mimicActions";
import { useVariablesBatchQuery } from "../../../hooks/useVariablesQuery";
import { useDashboardContext } from "../DashboardContext";
import { resolveWidgetPath } from "../dashboardUtils";
import DashWidgetShell from "../DashWidgetShell";
import { cloneRecord, setFieldValue } from "../../../utils/record";
import { asBool } from "../../../scada/utils";
import ScadaMimicCanvas from "../../scada/ScadaMimicCanvas";
import { exportMimicSvgToPng, resolveMimicExportBackground } from "../../../scada/mimicPngExport";

interface ScadaMimicWidgetViewProps {
  widget: ScadaMimicWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

const MIN_ZOOM = 0.25;
const MAX_ZOOM = 4;

function clampZoom(value: number): number {
  return Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, value));
}

function defaultViewState(defaultZoom: number | undefined) {
  return {
    panX: 0,
    panY: 0,
    zoom: clampZoom(defaultZoom ?? 1),
  };
}

function isViewChanged(
  view: { panX: number; panY: number; zoom: number },
  baseline: { panX: number; panY: number; zoom: number }
): boolean {
  return (
    Math.abs(view.panX - baseline.panX) > 0.5
    || Math.abs(view.panY - baseline.panY) > 0.5
    || Math.abs(view.zoom - baseline.zoom) > 0.001
  );
}

function resolveActionSelection(action: MimicAction): Record<string, string> | undefined {
  if (action.selectionJson?.trim()) {
    return parseSelectionJson(action.selectionJson);
  }
  if (action.selectionKey?.trim() && action.objectPath?.trim()) {
    return { [action.selectionKey.trim()]: action.objectPath.trim() };
  }
  return undefined;
}

interface ContextMenuState {
  element: MimicElement;
  x: number;
  y: number;
}

export default function ScadaMimicWidgetView({
  widget,
  refreshIntervalMs,
  editable,
}: ScadaMimicWidgetViewProps) {
  const { t } = useTranslation("widgets");
  const session = useDashboardContext();
  const queryClient = useQueryClient();
  const [message, setMessage] = useState<string | null>(null);
  const [exportError, setExportError] = useState<string | null>(null);
  const viewportRef = useRef<HTMLDivElement>(null);
  const [view, setView] = useState(() => defaultViewState(widget.defaultZoom));
  const [runtimeDoc, setRuntimeDoc] = useState<ScadaMimicDocument | null>(null);
  const [contextMenu, setContextMenu] = useState<ContextMenuState | null>(null);
  const [localElements, setLocalElements] = useState<Record<string, MimicElement>>({});

  const panEnabled = widget.panEnabled !== false;
  const documentKey = `${widget.mimicPath ?? ""}:${widget.diagramJson ?? ""}`;
  const baselineView = useMemo(
    () => defaultViewState(widget.defaultZoom),
    [widget.defaultZoom]
  );

  useEffect(() => {
    setView(defaultViewState(widget.defaultZoom));
    setRuntimeDoc(null);
    setLocalElements({});
  }, [documentKey, widget.defaultZoom]);

  const mimicQuery = useQuery({
    queryKey: ["mimic", widget.mimicPath],
    queryFn: () => fetchMimic(widget.mimicPath!),
    enabled: Boolean(widget.mimicPath?.trim()),
  });

  const baseDocument = useMemo(() => {
    if (widget.mimicPath?.trim() && mimicQuery.data?.diagramJson) {
      return parseMimicDocument(mimicQuery.data.diagramJson);
    }
    return parseMimicDocument(widget.diagramJson);
  }, [widget.diagramJson, widget.mimicPath, mimicQuery.data?.diagramJson]);

  const document = useMemo(() => {
    const doc = runtimeDoc ?? baseDocument;
    if (Object.keys(localElements).length === 0) return doc;
    return {
      ...doc,
      elements: doc.elements.map((el) => localElements[el.id] ?? el),
    };
  }, [baseDocument, runtimeDoc, localElements]);

  const bindingPaths = useMemo(
    () => collectBindingPaths(document.elements, document.connections, session),
    [document, session]
  );

  const variablesBatch = useVariablesBatchQuery(bindingPaths, refreshIntervalMs, bindingPaths.length > 0);
  const variablesByPath = variablesBatch.data ?? {};

  const resolved = useMemo(
    () => resolveDocumentBindings(document.elements, document.connections, session, variablesByPath),
    [document, session, variablesByPath]
  );

  const elementTooltips = useMemo(() => {
    const out: Record<string, string | undefined> = {};
    for (const el of document.elements) {
      out[el.id] = buildElementTooltip(el, resolved.byElementId[el.id] ?? {});
    }
    return out;
  }, [document.elements, resolved.byElementId]);

  const runServerAction = useMutation({
    mutationFn: async (action: MimicAction) => {
      const objectPath = resolveWidgetPath(
        action.objectPath,
        action.selectionKey,
        session.selection,
        undefined,
        session.params
      );
      if (action.type === "invokeFunction") {
        if (!objectPath || !action.functionName) throw new Error(t("error.objectAndFunctionRequired"));
        return invokeFunction(objectPath, action.functionName);
      }
      if (action.type === "setVariable" || action.type === "toggleVariable") {
        if (!objectPath || !action.variableName) throw new Error(t("scadaMimic.error.bindingRequired"));
        const variables = variablesByPath[objectPath];
        const variable = variables?.find((v) => v.name === action.variableName);
        if (!variable?.value) throw new Error(t("scadaMimic.error.variableNotLoaded"));
        const field = action.valueField ?? "value";
        if (action.type === "toggleVariable") {
          const active = asBool(
            resolveBindingValue(
              {
                variableName: action.variableName,
                objectPath: action.objectPath,
                selectionKey: action.selectionKey,
                valueField: field,
              },
              session,
              variablesByPath
            )
          );
          const record = setFieldValue(cloneRecord(variable.value), field, !active);
          return setVariable(objectPath, action.variableName, record);
        }
        const record = setFieldValue(cloneRecord(variable.value), field, action.value);
        return setVariable(objectPath, action.variableName, record);
      }
      return null;
    },
    onSuccess: () => {
      setMessage(t("view.done"));
      queryClient.invalidateQueries({ queryKey: ["variables"] });
      queryClient.invalidateQueries({ queryKey: ["variables-batch"] });
    },
  });

  const executeAction = useCallback(
    async (element: MimicElement, action: MimicAction) => {
      if (action.confirmMessage && !window.confirm(action.confirmMessage)) return;

      switch (action.type) {
        case "navigate": {
          const selection = resolveActionSelection(action);
          if (action.dashboardPath?.trim()) {
            session.navigateToDashboard(action.dashboardPath.trim(), { selection });
          } else if (action.url?.trim()) {
            window.open(action.url.trim(), "_blank", "noopener,noreferrer");
          }
          break;
        }
        case "setSelection": {
          const selection = resolveActionSelection(action);
          if (selection) {
            for (const [key, path] of Object.entries(selection)) {
              session.setSelection(key, path);
            }
          }
          if (action.dashboardPath?.trim()) {
            session.navigateToDashboard(action.dashboardPath.trim(), { selection });
          }
          break;
        }
        case "toggleLayer": {
          if (!action.layerId) break;
          setRuntimeDoc((prev) => applyToggleLayer(prev ?? baseDocument, action.layerId!));
          break;
        }
        case "cycleUnit": {
          setLocalElements((prev) => ({
            ...prev,
            [element.id]: applyCycleUnit(prev[element.id] ?? element, action),
          }));
          break;
        }
        case "toggleExpand": {
          setLocalElements((prev) => ({
            ...prev,
            [element.id]: applyToggleExpand(prev[element.id] ?? element, action),
          }));
          break;
        }
        case "setVariable":
        case "toggleVariable":
        case "invokeFunction":
          await runServerAction.mutateAsync(action);
          break;
        default:
          break;
      }
    },
    [baseDocument, runServerAction, session]
  );

  const handleElementClick = useCallback(
    (element: MimicElement) => {
      setContextMenu(null);
      const action = primaryAction(element);
      if (!action) return;
      void executeAction(element, action);
    },
    [executeAction]
  );

  const handleElementContextMenu = useCallback(
    (element: MimicElement, clientX: number, clientY: number) => {
      const items = contextActions(element);
      if (items.length === 0) return;
      setContextMenu({ element, x: clientX, y: clientY });
    },
    []
  );

  const hasInteractions = document.elements.some(
    (el) => (el.actions?.length ?? 0) > 0 || el.tooltip
  );
  const viewChanged = panEnabled && isViewChanged(view, baselineView);

  const resetView = useCallback(() => {
    setView(baselineView);
  }, [baselineView]);

  const exportPng = useCallback(async () => {
    setExportError(null);
    setMessage(null);
    const svg = viewportRef.current?.querySelector("svg.scada-mimic-svg");
    if (!svg || !(svg instanceof SVGSVGElement)) {
      setExportError(t("view.scadaMimic.exportFailed"));
      return;
    }
    const slug = (widget.mimicPath?.split(".").pop() || "scada-mimic").replace(/[^\w-]+/g, "-");
    try {
      await exportMimicSvgToPng(svg, {
        width: document.width,
        height: document.height,
        filename: `${slug}.png`,
        backgroundColor: resolveMimicExportBackground(document.background),
      });
      setMessage(t("view.scadaMimic.exportDone"));
    } catch {
      setExportError(t("view.scadaMimic.exportFailed"));
    }
  }, [document.background, document.height, document.width, t, widget.mimicPath]);

  useEffect(() => {
    const node = viewportRef.current;
    if (!node || !panEnabled) return;

    const onWheel = (event: WheelEvent) => {
      event.preventDefault();
      event.stopPropagation();

      const rect = node.getBoundingClientRect();
      const mx = event.clientX - rect.left;
      const my = event.clientY - rect.top;
      const factor = event.deltaY < 0 ? 1.12 : 1 / 1.12;

      setView((current) => {
        const nextZoom = clampZoom(current.zoom * factor);
        if (nextZoom === current.zoom) return current;
        return {
          zoom: nextZoom,
          panX: mx - ((mx - current.panX) / current.zoom) * nextZoom,
          panY: my - ((my - current.panY) / current.zoom) * nextZoom,
        };
      });
    };

    node.addEventListener("wheel", onWheel, { passive: false });
    return () => node.removeEventListener("wheel", onWheel);
  }, [panEnabled]);

  useEffect(() => {
    if (!contextMenu) return;
    const close = () => setContextMenu(null);
    window.addEventListener("click", close);
    return () => window.removeEventListener("click", close);
  }, [contextMenu]);

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-scada-mimic"
      editable={editable}
    >
      <div ref={viewportRef} className="scada-mimic-viewport">
        <div className="scada-mimic-view-controls">
          {viewChanged && (
            <button
              type="button"
              className="scada-mimic-reset-zoom btn small"
              title={t("view.scadaMimic.resetZoom")}
              onClick={(e) => {
                e.stopPropagation();
                resetView();
              }}
              onMouseDown={(e) => e.stopPropagation()}
            >
              {t("view.scadaMimic.resetZoom")}
            </button>
          )}
          {!editable && (
            <button
              type="button"
              className="scada-mimic-export-png btn small"
              title={t("view.scadaMimic.exportPng")}
              onClick={(e) => {
                e.stopPropagation();
                void exportPng();
              }}
              onMouseDown={(e) => e.stopPropagation()}
            >
              {t("view.scadaMimic.exportPng")}
            </button>
          )}
        </div>
        <ScadaMimicCanvas
          document={document}
          valuesByElementId={resolved.byElementId}
          valuesByConnectionId={resolved.byConnectionId}
          editable={editable}
          customSymbols={document.customSymbols}
          onElementClick={hasInteractions ? handleElementClick : undefined}
          onElementContextMenu={handleElementContextMenu}
          elementTooltips={elementTooltips}
          viewTransform={
            panEnabled
              ? { panX: view.panX, panY: view.panY, zoom: view.zoom }
              : baselineView
          }
        />
        {contextMenu && (
          <ul
            className="scada-mimic-context-menu"
            style={{ left: contextMenu.x, top: contextMenu.y }}
            onClick={(e) => e.stopPropagation()}
          >
            {contextActions(contextMenu.element).map((action) => (
              <li key={action.id}>
                <button
                  type="button"
                  onClick={() => {
                    void executeAction(contextMenu.element, action);
                    setContextMenu(null);
                  }}
                >
                  {action.label ?? action.type}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
      {message && <p className="widget-message success">{message}</p>}
      {exportError && <p className="widget-message error">{exportError}</p>}
      {runServerAction.isError && (
        <p className="widget-message error">{(runServerAction.error as Error).message}</p>
      )}
    </DashWidgetShell>
  );
}
