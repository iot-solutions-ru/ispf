import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  applySvgBehaviorsToRoot,
  prepareSvgInner,
} from "../../../scada/svgSymbolEngine";
import {
  collectTopologyBindingInterests,
  collectTopologyBindingPaths,
  extractSvgInnerFromDocument,
  groupVariablesByPath,
  resolveTopologyBindingValues,
} from "../../../scada/topologySvgConfig";
import { useVariablesBatchQuery } from "../../../hooks/useVariablesQuery";
import { triggerDashboardOpen, useDashboardContext } from "../DashboardContext";
import { resolveWidgetMediaSrc } from "../widgetMediaUrl";

export interface SvgInteractiveBodyProps {
  svgUrl?: string;
  config: import("../../../scada/svgWidgetInteractive").SvgInteractiveConfig;
  selectionKey?: string;
  hitTargetDashboard?: string;
  hitOpenMode?: "navigate" | "modal";
  showLegend?: boolean;
  panEnabled?: boolean;
  defaultZoom?: number;
  refreshIntervalMs: number;
  editable?: boolean;
  title?: string;
}

const MIN_ZOOM = 0.35;
const MAX_ZOOM = 3;

function clampZoom(value: number): number {
  return Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, value));
}

/** Inline SVG with live fill/stroke bindings (svg-widget topology mode). */
export default function SvgInteractiveBody({
  svgUrl,
  config,
  selectionKey,
  hitTargetDashboard,
  hitOpenMode,
  showLegend,
  panEnabled = true,
  defaultZoom = 1,
  refreshIntervalMs,
  editable,
  title,
}: SvgInteractiveBodyProps) {
  const session = useDashboardContext();
  const viewportRef = useRef<HTMLDivElement>(null);
  const layerRef = useRef<SVGGElement>(null);
  const mountedBaseRef = useRef<string | null>(null);
  const [zoom, setZoom] = useState(() => clampZoom(defaultZoom));
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [layerRevision, setLayerRevision] = useState(0);
  const panDragRef = useRef<{ x: number; y: number; panX: number; panY: number } | null>(null);

  const bindingPaths = useMemo(
    () => (config ? collectTopologyBindingPaths(config.bindings, session) : []),
    [config, session]
  );
  const bindingVariablesByPath = useMemo(
    () =>
      config
        ? groupVariablesByPath(collectTopologyBindingInterests(config.bindings, session))
        : {},
    [config, session]
  );

  const variablesBatch = useVariablesBatchQuery(
    bindingPaths,
    refreshIntervalMs,
    bindingPaths.length > 0,
    bindingVariablesByPath
  );

  const bindingValues = useMemo(
    () =>
      config
        ? resolveTopologyBindingValues(config.bindings, session, variablesBatch.data ?? {})
        : {},
    [config, session, variablesBatch.data]
  );

  const resolvedSvgUrl = resolveWidgetMediaSrc(svgUrl);
  const svgQuery = useQuery({
    queryKey: ["svg-widget-topology", resolvedSvgUrl],
    queryFn: async () => {
      const response = await fetch(resolvedSvgUrl);
      if (!response.ok) {
        throw new Error(`Failed to load SVG (${response.status})`);
      }
      return response.text();
    },
    enabled: Boolean(resolvedSvgUrl && !config?.svgInner),
    staleTime: 60_000,
  });

  const baseSvgInner = useMemo(() => {
    if (config?.svgInner?.trim()) {
      return prepareSvgInner(config.svgInner);
    }
    if (svgQuery.data) {
      return prepareSvgInner(extractSvgInnerFromDocument(svgQuery.data));
    }
    return "";
  }, [config?.svgInner, svgQuery.data]);

  // Mount static geometry once; status colors are patched in place below.
  useEffect(() => {
    const layer = layerRef.current;
    if (!layer || !baseSvgInner) return;
    if (mountedBaseRef.current === baseSvgInner) return;
    layer.innerHTML = baseSvgInner;
    mountedBaseRef.current = baseSvgInner;
    setLayerRevision((value) => value + 1);
  }, [baseSvgInner]);

  useEffect(() => {
    const layer = layerRef.current;
    if (!layer || !config || !mountedBaseRef.current) return;
    applySvgBehaviorsToRoot({
      root: layer,
      values: bindingValues,
      behaviors: config.behaviors,
    });
  }, [bindingValues, config, layerRevision]);

  const selectedPath = selectionKey ? session.selection[selectionKey] : undefined;

  useEffect(() => {
    const host = viewportRef.current;
    if (!host || editable || !config || !layerRevision) return;

    const cleanups: Array<() => void> = [];
    for (const area of config.hitAreas) {
      const targetId = area.id ?? `back_${area.nodeName}`;
      const el = host.querySelector(`#${CSS.escape(targetId)}`);
      if (!el) continue;
      el.setAttribute("data-topology-node", area.nodeName);
      (el as SVGElement).style.cursor = "pointer";
      const handler = (event: Event) => {
        event.stopPropagation();
        if (!selectionKey?.trim()) return;
        session.setSelection(selectionKey, area.objectPath);
        triggerDashboardOpen(
          hitOpenMode,
          hitTargetDashboard,
          title,
          {
            navigateToDashboard: session.navigateToDashboard,
            openDashboardModal: session.openDashboardModal,
          },
          { selection: { [selectionKey]: area.objectPath } }
        );
      };
      el.addEventListener("click", handler);
      cleanups.push(() => el.removeEventListener("click", handler));
    }

    return () => {
      for (const cleanup of cleanups) cleanup();
    };
  }, [config, editable, hitOpenMode, hitTargetDashboard, layerRevision, selectionKey, session, title]);

  useEffect(() => {
    const host = viewportRef.current;
    if (!host || !selectedPath) return;
    for (const el of host.querySelectorAll("[data-topology-selected]")) {
      el.removeAttribute("data-topology-selected");
      el.removeAttribute("stroke");
      el.removeAttribute("stroke-width");
    }
    const area = config?.hitAreas.find((hit) => hit.objectPath === selectedPath);
    if (!area) return;
    const targetId = area.id ?? `back_${area.nodeName}`;
    const el = host.querySelector(`#${CSS.escape(targetId)}`);
    if (!el) return;
    el.setAttribute("data-topology-selected", "1");
    el.setAttribute("stroke", "#1e3a5f");
    el.setAttribute("stroke-width", "3");
  }, [config?.hitAreas, layerRevision, selectedPath, bindingValues]);

  const onWheel = useCallback(
    (event: React.WheelEvent) => {
      if (!panEnabled || editable) return;
      event.preventDefault();
      const delta = event.deltaY > 0 ? -0.08 : 0.08;
      setZoom((value) => clampZoom(value + delta));
    },
    [editable, panEnabled]
  );

  const onPointerDown = useCallback(
    (event: React.PointerEvent) => {
      if (!panEnabled || editable || event.button !== 0) return;
      panDragRef.current = { x: event.clientX, y: event.clientY, panX: pan.x, panY: pan.y };
      (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
    },
    [editable, pan.x, pan.y, panEnabled]
  );

  const onPointerMove = useCallback((event: React.PointerEvent) => {
    const drag = panDragRef.current;
    if (!drag) return;
    setPan({
      x: drag.panX + (event.clientX - drag.x),
      y: drag.panY + (event.clientY - drag.y),
    });
  }, []);

  const onPointerUp = useCallback((event: React.PointerEvent) => {
    if (!panDragRef.current) return;
    panDragRef.current = null;
    try {
      (event.currentTarget as HTMLElement).releasePointerCapture(event.pointerId);
    } catch {
      /* ignore */
    }
  }, []);

  const resetView = () => {
    setZoom(clampZoom(defaultZoom));
    setPan({ x: 0, y: 0 });
  };

  const legend = showLegend !== false;
  const waitingForBase = !baseSvgInner && (svgQuery.isLoading || Boolean(resolvedSvgUrl && !config?.svgInner));

  if (!config) {
    return <p className="hint">Укажите behaviorsJson и bindingsJson (как у SCADA-символа).</p>;
  }
  if (variablesBatch.isError) {
    return <p className="function-widget-msg error">{String(variablesBatch.error)}</p>;
  }
  if (svgQuery.isError) {
    return <p className="function-widget-msg error">{String(svgQuery.error)}</p>;
  }
  if (waitingForBase) {
    return <p className="hint">Загрузка SVG…</p>;
  }
  if (!baseSvgInner) {
    return <p className="hint">SVG не загружен.</p>;
  }

  return (
    <div
      ref={viewportRef}
      className="topology-svg-viewport"
      style={{ background: config.backgroundColor }}
      onWheel={onWheel}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerCancel={onPointerUp}
    >
      {panEnabled ? (
        <div className="topology-svg-view-controls">
          <button type="button" className="topology-svg-reset-zoom btn small" onClick={resetView}>
            100%
          </button>
        </div>
      ) : null}
      <div
        className="topology-svg-stage"
        style={{
          transform: `translate(${pan.x}px, ${pan.y}px) scale(${zoom})`,
        }}
      >
        <svg
          className="topology-svg-canvas"
          viewBox={config.viewBox}
          preserveAspectRatio="xMidYMid meet"
          role="img"
          aria-label={title ?? "SVG diagram"}
        >
          <g ref={layerRef} className="topology-svg-layer" />
        </svg>
      </div>
      {legend ? (
        <div className="topology-svg-legend" aria-hidden>
          <span>
            <i className="topology-svg-dot online" /> Узел online
          </span>
          <span>
            <i className="topology-svg-dot offline" /> Узел offline
          </span>
          <span>
            <i className="topology-svg-dot link-up" /> Линк up
          </span>
          <span>
            <i className="topology-svg-dot link-down" /> Линк down
          </span>
        </div>
      ) : null}
    </div>
  );
}
