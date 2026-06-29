import { useMemo } from "react";
import type { MimicConnection, MimicCustomSymbol, MimicElement, ScadaMimicDocument } from "../../types/scadaMimic";
import { applyFormatRules } from "../../scada/formatRules";
import { connectionPolylinePoints, getElementPortPosition } from "../../scada/connectionRouting";
import { elementRenderProps } from "../../scada/elementRenderProps";
import { getSymbolRender, resolveElementSymbol, symbolSize } from "../../scada/symbols/registry";
import { asBool } from "../../scada/utils";

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
  onSelectElement?: (id: string, additive?: boolean) => void;
  onSelectConnection?: (id: string | null) => void;
  onElementClick?: (element: MimicElement) => void;
  onCanvasClick?: (x: number, y: number) => void;
  onConnectAtPoint?: (x: number, y: number) => void;
  onElementConnectClick?: (element: MimicElement, x: number, y: number) => void;
  onElementDrag?: (id: string, x: number, y: number) => void;
  onElementDragEnd?: () => void;
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
  onSelectElement,
  onSelectConnection,
  onElementClick,
  onCanvasClick,
  onConnectAtPoint,
  onElementConnectClick,
  onElementDrag,
  onElementDragEnd,
  customSymbols,
  viewTransform,
}: ScadaMimicCanvasProps) {
  const visibleLayers = useMemo(
    () => new Set(document.layers.filter((l) => l.visible).map((l) => l.id)),
    [document.layers]
  );

  const elements = document.elements.filter((el) => visibleLayers.has(el.layerId));
  const connections = document.connections.filter((c) => visibleLayers.has(c.layerId));

  const portHints = useMemo(() => {
    if (!connectMode) return [];
    const hints: {
      key: string;
      x: number;
      y: number;
      active: boolean;
    }[] = [];
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
  }, [connectMode, connectFrom, elements]);

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
            <path d={`M ${gridSize} 0 L 0 0 0 ${gridSize}`} fill="none" stroke="var(--border)" strokeWidth={0.5} opacity={0.35} />
          </pattern>
        </defs>
      )}
      {showGrid && (
        <rect width={document.width} height={document.height} fill="url(#scada-grid)" pointerEvents="none" />
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
        const transform = `translate(${el.x},${el.y}) rotate(${el.rotation ?? 0} ${width / 2} ${height / 2})`;
        return (
          <g
            key={el.id}
            transform={transform}
            onClick={(e) => {
              e.stopPropagation();
              const svg = e.currentTarget.ownerSVGElement;
              if (!svg) return;
              const local = clientToSvg(svg, e.clientX, e.clientY);
              if (!local) return;
              if (connectMode && onElementConnectClick) {
                onElementConnectClick(el, local.x, local.y);
                return;
              }
              if (editMode) {
                onSelectElement?.(el.id, e.shiftKey);
              } else if (onElementClick) {
                onElementClick(el);
              }
            }}
            onMouseDown={(e) => {
              if (!editMode || connectMode || !onElementDrag || e.button !== 0) return;
              e.stopPropagation();
              onSelectElement?.(el.id);
              const startX = e.clientX;
              const startY = e.clientY;
              const origX = el.x;
              const origY = el.y;
              let dragged = false;
              const svg = e.currentTarget.ownerSVGElement as SVGSVGElement | null;
              const move = (ev: MouseEvent) => {
                if (!svg) return;
                dragged = true;
                const scale = svg.clientWidth / document.width;
                const dx = (ev.clientX - startX) / scale;
                const dy = (ev.clientY - startY) / scale;
                onElementDrag(el.id, origX + dx, origY + dy);
              };
              const up = () => {
                window.removeEventListener("mousemove", move);
                window.removeEventListener("mouseup", up);
                if (dragged) {
                  onElementDragEnd?.();
                }
              };
              window.addEventListener("mousemove", move);
              window.addEventListener("mouseup", up);
            }}
            style={{
              cursor: connectMode ? "crosshair" : editMode ? "move" : onElementClick ? "pointer" : undefined,
            }}
          >
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
        );
      })}
    </svg>
    </div>
  );
}
