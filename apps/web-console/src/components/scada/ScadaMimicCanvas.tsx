import { useMemo } from "react";
import type { MimicConnection, MimicCustomSymbol, MimicElement, ScadaMimicDocument } from "../../types/scadaMimic";
import type { SnapGuide } from "../../scada/elementSnap";
import { applyFormatRules } from "../../scada/formatRules";
import { connectionPolylinePoints, getElementPortPosition } from "../../scada/connectionRouting";
import { elementRenderProps } from "../../scada/elementRenderProps";
import { elementFlipTransform, elementTransform, type ResizeHandle } from "../../scada/layoutOps";
import { getSymbolRender, resolveElementSymbol, symbolSize } from "../../scada/symbols/registry";
import { asBool } from "../../scada/utils";

const HANDLE_SIZE = 8;
const HANDLES: { id: ResizeHandle; fx: number; fy: number; cursor: string }[] = [
  { id: "nw", fx: 0, fy: 0, cursor: "nwse-resize" },
  { id: "n", fx: 0.5, fy: 0, cursor: "ns-resize" },
  { id: "ne", fx: 1, fy: 0, cursor: "nesw-resize" },
  { id: "e", fx: 1, fy: 0.5, cursor: "ew-resize" },
  { id: "se", fx: 1, fy: 1, cursor: "nwse-resize" },
  { id: "s", fx: 0.5, fy: 1, cursor: "ns-resize" },
  { id: "sw", fx: 0, fy: 1, cursor: "nesw-resize" },
  { id: "w", fx: 0, fy: 0.5, cursor: "ew-resize" },
];

export interface ScadaMimicCanvasProps {
  document: ScadaMimicDocument;
  valuesByElementId: Record<string, Record<string, unknown>>;
  valuesByConnectionId: Record<string, Record<string, unknown>>;
  editable?: boolean;
  editMode?: boolean;
  connectMode?: boolean;
  connectFrom?: { elementId: string; port: string } | null;
  selectedIds?: Set<string>;
  selectedConnectionId?: string | null;
  snapGuides?: SnapGuide[];
  showResizeHandles?: boolean;
  onSelectElement?: (id: string, additive?: boolean) => void;
  onSelectConnection?: (id: string | null) => void;
  onElementClick?: (element: MimicElement) => void;
  onCanvasClick?: (x: number, y: number) => void;
  onConnectAtPoint?: (x: number, y: number) => void;
  onElementConnectClick?: (element: MimicElement, x: number, y: number) => void;
  onElementDragStart?: (id: string, origins: Map<string, { x: number; y: number }>) => void;
  onElementDrag?: (id: string, x: number, y: number) => void;
  onElementDragEnd?: () => void;
  onElementResize?: (id: string, handle: ResizeHandle, dx: number, dy: number, aspectLock: boolean) => void;
  onElementResizeStart?: (id: string) => void;
  onElementResizeEnd?: () => void;
  customSymbols?: MimicCustomSymbol[];
  viewTransform?: { panX: number; panY: number; zoom: number };
}

function clientToSvg(svg: SVGSVGElement, clientX: number, clientY: number): { x: number; y: number } | null {
  const pt = svg.createSVGPoint();
  pt.x = clientX;
  pt.y = clientY;
  const ctm = svg.getScreenCTM();
  if (!ctm) return null;
  const local = pt.matrixTransform(ctm.inverse());
  return { x: local.x, y: local.y };
}

function svgPointerDelta(
  svg: SVGSVGElement,
  startClientX: number,
  startClientY: number,
  clientX: number,
  clientY: number
): { dx: number; dy: number } | null {
  const start = clientToSvg(svg, startClientX, startClientY);
  const current = clientToSvg(svg, clientX, clientY);
  if (!start || !current) return null;
  return { dx: current.x - start.x, dy: current.y - start.y };
}

function ConnectionPath({
  connection,
  elements,
  values,
  selected,
  onSelect,
  customSymbols,
}: {
  connection: MimicConnection;
  elements: MimicElement[];
  values: Record<string, unknown>;
  selected?: boolean;
  onSelect?: () => void;
  customSymbols?: MimicCustomSymbol[];
}) {
  const points = connectionPolylinePoints(connection, elements, customSymbols);
  if (points.length < 2) return null;
  const d = points.map((p: { x: number; y: number }, i: number) => `${i === 0 ? "M" : "L"} ${p.x} ${p.y}`).join(" ");
  const flowing = asBool(values.flowing);
  const alarm = asBool(values.alarm);
  const stroke = alarm ? "#f85149" : flowing ? "#58a6ff" : connection.style?.stroke ?? "#8b949e";
  return (
    <path
      d={d}
      fill="none"
      stroke={stroke}
      strokeWidth={connection.style?.strokeWidth ?? 3}
      strokeDasharray={connection.style?.dash}
      onClick={(e) => {
        e.stopPropagation();
        onSelect?.();
      }}
      style={{ cursor: onSelect ? "pointer" : undefined }}
      opacity={selected ? 1 : 0.95}
    />
  );
}

export default function ScadaMimicCanvas({
  document,
  valuesByElementId,
  valuesByConnectionId,
  editable,
  editMode,
  connectMode,
  connectFrom,
  selectedIds,
  selectedConnectionId,
  snapGuides,
  showResizeHandles,
  onSelectElement,
  onSelectConnection,
  onElementClick,
  onCanvasClick,
  onConnectAtPoint,
  onElementConnectClick,
  onElementDragStart,
  onElementDrag,
  onElementDragEnd,
  onElementResize,
  onElementResizeStart,
  onElementResizeEnd,
  customSymbols,
  viewTransform,
}: ScadaMimicCanvasProps) {
  const visibleLayers = useMemo(
    () => new Set(document.layers.filter((l) => l.visible).map((l) => l.id)),
    [document.layers]
  );

  const elements = document.elements.filter((el) => visibleLayers.has(el.layerId));
  const connections = document.connections.filter((c) => visibleLayers.has(c.layerId));
  const singleSelectedId =
    selectedIds && selectedIds.size === 1 ? [...selectedIds][0] : null;

  const portHints = useMemo(() => {
    if (!connectMode) return [];
    const hints: { key: string; x: number; y: number; active: boolean }[] = [];
    for (const el of elements) {
      const symbol = resolveElementSymbol(el, customSymbols);
      if (!symbol) continue;
      for (const port of symbol.ports) {
        const pos = getElementPortPosition(el, port.id, customSymbols);
        if (!pos) continue;
        hints.push({
          key: `${el.id}-${port.id}`,
          x: pos.x,
          y: pos.y,
          active: connectFrom?.elementId === el.id && connectFrom.port === port.id,
        });
      }
    }
    return hints;
  }, [connectMode, connectFrom, elements, customSymbols]);

  const handleSvgClick = (e: React.MouseEvent<SVGSVGElement>) => {
    if (!editMode) return;
    const local = clientToSvg(e.currentTarget, e.clientX, e.clientY);
    if (!local) return;
    if (connectMode && onConnectAtPoint) {
      onConnectAtPoint(local.x, local.y);
      return;
    }
    onCanvasClick?.(local.x, local.y);
  };

  const gridSize = document.grid?.size ?? 1;
  const showGrid = editMode && document.grid?.visible === true && gridSize > 1;

  const buildDragOrigins = (leaderId: string, useSelection: boolean) => {
    const origins = new Map<string, { x: number; y: number }>();
    const dragIds =
      useSelection && selectedIds?.has(leaderId) && selectedIds.size > 0
        ? selectedIds
        : new Set([leaderId]);
    for (const item of document.elements) {
      if (dragIds.has(item.id)) origins.set(item.id, { x: item.x, y: item.y });
    }
    return origins;
  };

  return (
    <div
      className={`scada-mimic-artboard${connectMode ? " scada-mimic-artboard-connect" : ""}`}
      style={{ width: document.width, height: document.height }}
    >
      <svg
        className="scada-mimic-svg"
        viewBox={`0 0 ${document.width} ${document.height}`}
        width={document.width}
        height={document.height}
        preserveAspectRatio="xMidYMid meet"
        style={{
          background: document.background ?? "var(--bg)",
          transform: viewTransform
            ? `translate(${viewTransform.panX}px, ${viewTransform.panY}px) scale(${viewTransform.zoom})`
            : undefined,
          transformOrigin: "0 0",
        }}
        onClick={handleSvgClick}
      >
        {showGrid && (
          <defs>
            <pattern id="scada-grid" width={gridSize} height={gridSize} patternUnits="userSpaceOnUse">
              <path
                d={`M ${gridSize} 0 L 0 0 0 ${gridSize}`}
                fill="none"
                stroke="var(--border)"
                strokeWidth={0.5}
                opacity={0.35}
              />
            </pattern>
          </defs>
        )}
        {showGrid && (
          <rect width={document.width} height={document.height} fill="url(#scada-grid)" pointerEvents="none" />
        )}

        {snapGuides?.map((guide, index) =>
          guide.orientation === "v" ? (
            <line
              key={`guide-v-${index}`}
              className="scada-snap-guide"
              x1={guide.position}
              y1={0}
              x2={guide.position}
              y2={document.height}
              pointerEvents="none"
            />
          ) : (
            <line
              key={`guide-h-${index}`}
              className="scada-snap-guide"
              x1={0}
              y1={guide.position}
              x2={document.width}
              y2={guide.position}
              pointerEvents="none"
            />
          )
        )}

        {connections.map((conn) => (
          <ConnectionPath
            key={conn.id}
            connection={conn}
            elements={document.elements}
            values={valuesByConnectionId[conn.id] ?? {}}
            selected={selectedConnectionId === conn.id}
            onSelect={editMode && !connectMode ? () => onSelectConnection?.(conn.id) : undefined}
            customSymbols={customSymbols}
          />
        ))}

        {portHints.map((hint) => (
          <g key={hint.key} pointerEvents="none">
            <circle
              className={`scada-port-hint${hint.active ? " scada-port-hint-active" : ""}`}
              cx={hint.x}
              cy={hint.y}
              r={hint.active ? 7 : 5}
            />
          </g>
        ))}

        {elements.map((el) => {
          const symbol = resolveElementSymbol(el, customSymbols);
          const Render = getSymbolRender(el.symbolId, customSymbols);
          if (!symbol || !Render) return null;
          const { width, height } = symbolSize(el, customSymbols);
          const values = valuesByElementId[el.id] ?? {};
          const styleOverrides = applyFormatRules(values, el.formatRules);
          const selected = selectedIds?.has(el.id);
          const showHandles =
            showResizeHandles && editMode && !connectMode && singleSelectedId === el.id;
          const transform = elementTransform(el, width, height);
          const flipTransform = elementFlipTransform(el, width, height);
          return (
            <g
              key={el.id}
              transform={transform}
              onClick={(e) => {
                e.stopPropagation();
                if (connectMode && onElementConnectClick) {
                  const svg = e.currentTarget.ownerSVGElement;
                  if (!svg) return;
                  const local = clientToSvg(svg, e.clientX, e.clientY);
                  if (!local) return;
                  onElementConnectClick(el, local.x, local.y);
                  return;
                }
                if (!editMode && onElementClick) {
                  onElementClick(el);
                }
              }}
              onMouseDown={(e) => {
                if (!editMode || connectMode || e.button !== 0) return;
                if ((e.target as Element).closest(".scada-resize-handle")) return;
                e.stopPropagation();
                e.preventDefault();

                const alreadySelected = selectedIds?.has(el.id) ?? false;
                const additive = e.shiftKey;

                if (additive) {
                  onSelectElement?.(el.id, true);
                  if (alreadySelected) return;
                } else if (!alreadySelected) {
                  onSelectElement?.(el.id, false);
                }

                if (!onElementDrag) return;

                const useGroupDrag = alreadySelected && !additive;
                const origins = buildDragOrigins(el.id, useGroupDrag);
                onElementDragStart?.(el.id, origins);
                const startX = e.clientX;
                const startY = e.clientY;
                const origX = el.x;
                const origY = el.y;
                let dragged = false;
                const svg = e.currentTarget.ownerSVGElement as SVGSVGElement | null;
                const move = (ev: MouseEvent) => {
                  if (!svg) return;
                  const delta = svgPointerDelta(svg, startX, startY, ev.clientX, ev.clientY);
                  if (!delta) return;
                  if (Math.abs(delta.dx) > 0.5 || Math.abs(delta.dy) > 0.5) dragged = true;
                  onElementDrag(el.id, origX + delta.dx, origY + delta.dy);
                };
                const up = () => {
                  window.removeEventListener("mousemove", move);
                  window.removeEventListener("mouseup", up);
                  if (dragged) onElementDragEnd?.();
                };
                window.addEventListener("mousemove", move);
                window.addEventListener("mouseup", up);
              }}
              style={{
                cursor: connectMode ? "crosshair" : editMode ? "move" : onElementClick ? "pointer" : undefined,
              }}
            >
              {showHandles &&
                HANDLES.map((handle) => {
                  const hx = handle.fx * width - HANDLE_SIZE / 2;
                  const hy = handle.fy * height - HANDLE_SIZE / 2;
                  return (
                    <rect
                      key={handle.id}
                      className="scada-resize-handle"
                      x={hx}
                      y={hy}
                      width={HANDLE_SIZE}
                      height={HANDLE_SIZE}
                      style={{ cursor: handle.cursor }}
                      onMouseDown={(e) => {
                        if (!onElementResize) return;
                        e.stopPropagation();
                        e.preventDefault();
                        onElementResizeStart?.(el.id);
                        const startX = e.clientX;
                        const startY = e.clientY;
                        let resized = false;
                        const svg = (e.currentTarget as SVGRectElement).ownerSVGElement;
                        const move = (ev: MouseEvent) => {
                          if (!svg) return;
                          const delta = svgPointerDelta(svg, startX, startY, ev.clientX, ev.clientY);
                          if (!delta) return;
                          if (Math.abs(delta.dx) > 0.5 || Math.abs(delta.dy) > 0.5) resized = true;
                          onElementResize(el.id, handle.id, delta.dx, delta.dy, ev.shiftKey);
                        };
                        const up = () => {
                          window.removeEventListener("mousemove", move);
                          window.removeEventListener("mouseup", up);
                          if (resized) onElementResizeEnd?.();
                        };
                        window.addEventListener("mousemove", move);
                        window.addEventListener("mouseup", up);
                      }}
                    />
                  );
                })}
              <g transform={flipTransform}>
                <Render
                  width={width}
                  height={height}
                  values={values}
                  props={elementRenderProps(el, symbol)}
                  styleOverrides={styleOverrides}
                  editable={editable}
                  selected={selected}
                />
              </g>
            </g>
          );
        })}
      </svg>
    </div>
  );
}
