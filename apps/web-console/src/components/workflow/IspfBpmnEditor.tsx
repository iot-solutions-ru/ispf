import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type MouseEvent as ReactMouseEvent,
  type PointerEvent as ReactPointerEvent,
} from "react";
import { useTranslation } from "react-i18next";
import { adaptForeignBpmn } from "../../bpmn/adaptForeignBpmn";
import { deserializeWorkflowDiagram } from "../../bpmn/model/deserialize";
import { serializeWorkflowDiagram } from "../../bpmn/model/serialize";
import {
  NODE_DEFAULT_SIZE,
  newNodeId,
  type FlowEdge,
  type FlowNode,
  type WorkflowDiagram,
} from "../../bpmn/model/types";
import { PLATFORM_PALETTE, type PalettePreset } from "../../bpmn/palettePresets";
import BpmnNodeGlyph from "./BpmnNodeGlyph";
import BpmnSequenceFlow from "./BpmnSequenceFlow";
import IspfBpmnPropertiesPanel, { type Selection } from "./IspfBpmnPropertiesPanel";

interface Props {
  xml: string;
  onChange: (xml: string) => void;
}

type Tool = "select" | "connect";

export default function IspfBpmnEditor({ xml, onChange }: Props) {
  const { t } = useTranslation("workflow");
  const [diagram, setDiagram] = useState<WorkflowDiagram>(() => {
    try {
      return deserializeWorkflowDiagram(xml);
    } catch {
      return adaptForeignBpmn(xml).diagram;
    }
  });
  const [selection, setSelection] = useState<Selection>(null);
  const [tool, setTool] = useState<Tool>("select");
  const [connectFrom, setConnectFrom] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [importWarnings, setImportWarnings] = useState<string[]>([]);
  const dragRef = useRef<{ id: string; ox: number; oy: number; mx: number; my: number } | null>(
    null
  );
  const lastEmittedXml = useRef<string | null>(null);
  const diagramRef = useRef(diagram);
  diagramRef.current = diagram;
  const svgRef = useRef<SVGSVGElement>(null);

  // Load external xml changes (XML tab / parent), ignore echo of our own emit
  useEffect(() => {
    const incoming = xml?.trim() || "";
    if (lastEmittedXml.current != null && incoming === lastEmittedXml.current.trim()) {
      return;
    }
    try {
      const next = deserializeWorkflowDiagram(xml);
      setDiagram(next);
      setError(null);
      setImportWarnings([]);
    } catch {
      const adapted = adaptForeignBpmn(xml);
      setDiagram(adapted.diagram);
      setImportWarnings(adapted.warnings.map((w) => w.message));
      setError(null);
    }
  }, [xml]);

  const emit = useCallback(
    (next: WorkflowDiagram) => {
      setDiagram(next);
      const out = serializeWorkflowDiagram(next);
      lastEmittedXml.current = out;
      onChange(out);
    },
    [onChange]
  );

  const selectedNode = selection?.kind === "node" ? selection.node : null;

  const viewBox = useMemo(() => {
    if (!diagram.nodes.length) return "0 0 800 400";
    let maxX = 400;
    let maxY = 300;
    for (const n of diagram.nodes) {
      maxX = Math.max(maxX, n.x + n.width + 80);
      maxY = Math.max(maxY, n.y + n.height + 80);
    }
    return `0 0 ${Math.max(800, maxX)} ${Math.max(400, maxY)}`;
  }, [diagram.nodes]);

  function addPreset(preset: PalettePreset) {
    const size = NODE_DEFAULT_SIZE[preset.type];
    const node: FlowNode = {
      id: newNodeId(preset.type),
      type: preset.type,
      name: preset.name,
      x: 120 + diagram.nodes.length * 24,
      y: 80 + (diagram.nodes.length % 5) * 20,
      width: size.width,
      height: size.height,
      ispf: { ...(preset.ispf || {}) },
      catchKind: preset.catchKind,
    };
    emit({ ...diagram, nodes: [...diagram.nodes, node] });
    setSelection({ kind: "node", node });
    setTool("select");
  }

  function updateNode(id: string, patch: Partial<FlowNode>) {
    const nodes = diagram.nodes.map((n) => {
      if (n.id !== id) return n;
      const next = { ...n, ...patch, ispf: patch.ispf ? { ...patch.ispf } : n.ispf };
      return next;
    });
    const nextDiagram = { ...diagram, nodes };
    emit(nextDiagram);
    const node = nodes.find((n) => n.id === id);
    if (node) setSelection({ kind: "node", node });
  }

  function updateEdge(id: string, patch: Partial<FlowEdge>) {
    const edges = diagram.edges.map((e) => (e.id === id ? { ...e, ...patch } : e));
    emit({ ...diagram, edges });
    const edge = edges.find((e) => e.id === id);
    if (edge) setSelection({ kind: "edge", edge });
  }

  function deleteSelection() {
    if (!selection) return;
    if (selection.kind === "node") {
      const id = selection.node.id;
      emit({
        ...diagram,
        nodes: diagram.nodes.filter((n) => n.id !== id),
        edges: diagram.edges.filter((e) => e.sourceRef !== id && e.targetRef !== id),
      });
    } else {
      const id = selection.edge.id;
      emit({ ...diagram, edges: diagram.edges.filter((e) => e.id !== id) });
    }
    setSelection(null);
  }

  function onNodeClick(node: FlowNode, e: ReactMouseEvent) {
    e.stopPropagation();
    if (tool === "connect") {
      if (!connectFrom) {
        setConnectFrom(node.id);
        return;
      }
      if (connectFrom === node.id) {
        setConnectFrom(null);
        return;
      }
      const edge: FlowEdge = {
        id: newNodeId("Flow"),
        sourceRef: connectFrom,
        targetRef: node.id,
      };
      emit({ ...diagram, edges: [...diagram.edges, edge] });
      setConnectFrom(null);
      setTool("select");
      setSelection({ kind: "edge", edge });
      return;
    }
    setSelection({ kind: "node", node });
  }

  function onPointerDownNode(node: FlowNode, e: ReactPointerEvent) {
    if (tool !== "select") return;
    e.stopPropagation();
    (e.target as Element).setPointerCapture?.(e.pointerId);
    dragRef.current = {
      id: node.id,
      ox: node.x,
      oy: node.y,
      mx: e.clientX,
      my: e.clientY,
    };
    setSelection({ kind: "node", node });
  }

  function onPointerMove(e: React.PointerEvent) {
    const drag = dragRef.current;
    if (!drag) return;
    const dx = e.clientX - drag.mx;
    const dy = e.clientY - drag.my;
    const nodes = diagram.nodes.map((n) =>
      n.id === drag.id ? { ...n, x: Math.max(0, drag.ox + dx), y: Math.max(0, drag.oy + dy) } : n
    );
    setDiagram({ ...diagram, nodes });
    const node = nodes.find((n) => n.id === drag.id);
    if (node) setSelection({ kind: "node", node });
  }

  function onPointerUp() {
    if (!dragRef.current) return;
    dragRef.current = null;
    const out = serializeWorkflowDiagram(diagramRef.current);
    lastEmittedXml.current = out;
    onChange(out);
  }

  function handleImportFile(file: File) {
    const reader = new FileReader();
    reader.onload = () => {
      const text = String(reader.result || "");
      const adapted = adaptForeignBpmn(text);
      setImportWarnings(adapted.warnings.map((w) => w.message));
      emit(adapted.diagram);
      setSelection(null);
    };
    reader.readAsText(file);
  }

  return (
    <div className="bpmn-editor-layout ispf-bpmn-editor">
      <div className="ispf-bpmn-palette">
        <div className="ispf-bpmn-tools">
          <button
            type="button"
            className={tool === "select" ? "active" : ""}
            onClick={() => {
              setTool("select");
              setConnectFrom(null);
            }}
            title={t("bpmn.tools.select")}
          >
            {t("bpmn.tools.select")}
          </button>
          <button
            type="button"
            className={tool === "connect" ? "active" : ""}
            onClick={() => setTool("connect")}
            title={t("bpmn.tools.connect")}
          >
            {t("bpmn.tools.connect")}
          </button>
          <label className="ispf-bpmn-import-btn">
            {t("bpmn.importFile")}
            <input
              type="file"
              accept=".bpmn,.xml,text/xml"
              hidden
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (f) handleImportFile(f);
                e.target.value = "";
              }}
            />
          </label>
        </div>
        {(["event", "activity", "gateway", "ai"] as const).map((group) => (
          <div key={group} className="ispf-bpmn-palette-group">
            <div className="ispf-bpmn-palette-group-title">{t(`bpmn.palette.group.${group}`)}</div>
            {PLATFORM_PALETTE.filter((p) => p.group === group).map((preset) => (
              <button key={preset.id} type="button" onClick={() => addPreset(preset)}>
                {t(preset.labelKey)}
              </button>
            ))}
          </div>
        ))}
      </div>

      <div className="bpmn-editor-wrap">
        {error && <p className="hint error bpmn-editor-error">{error}</p>}
        {importWarnings.length > 0 && (
          <div className="ispf-bpmn-import-report">
            <strong>{t("bpmn.importReport")}</strong>
            <ul>
              {importWarnings.map((w) => (
                <li key={w}>{w}</li>
              ))}
            </ul>
            <button type="button" className="btn" onClick={() => setImportWarnings([])}>
              {t("bpmn.dismissReport")}
            </button>
          </div>
        )}
        {tool === "connect" && (
          <p className="hint">
            {connectFrom ? t("bpmn.tools.connectPickTarget") : t("bpmn.tools.connectPickSource")}
          </p>
        )}
        <svg
          ref={svgRef}
          className="ispf-bpmn-canvas"
          viewBox={viewBox}
          onPointerMove={onPointerMove}
          onPointerUp={onPointerUp}
          onPointerLeave={onPointerUp}
          onClick={() => {
            if (tool === "select") setSelection(null);
          }}
        >
          <defs>
            <marker
              id="ispf-arrow"
              viewBox="0 0 10 10"
              refX="9"
              refY="5"
              markerWidth="7"
              markerHeight="7"
              orient="auto-start-reverse"
            >
              <path d="M 0 0 L 10 5 L 0 10 z" fill="currentColor" />
            </marker>
          </defs>
          {diagram.edges.map((edge) => {
            const s = diagram.nodes.find((n) => n.id === edge.sourceRef);
            const tgt = diagram.nodes.find((n) => n.id === edge.targetRef);
            if (!s || !tgt) return null;
            const selected = selection?.kind === "edge" && selection.edge.id === edge.id;
            return (
              <BpmnSequenceFlow
                key={edge.id}
                edge={edge}
                source={s}
                target={tgt}
                selected={selected}
                markerId="ispf-arrow"
                onClick={(e) => {
                  e.stopPropagation();
                  setSelection({ kind: "edge", edge });
                }}
              />
            );
          })}
          {diagram.nodes.map((node) => {
            const selected = selectedNode?.id === node.id;
            const connecting = connectFrom === node.id;
            return (
              <g
                key={node.id}
                className={`ispf-bpmn-node type-${node.type}${selected || connecting ? " selected" : ""}`}
                transform={`translate(${node.x},${node.y})`}
                onClick={(e) => onNodeClick(node, e)}
                onPointerDown={(e) => onPointerDownNode(node, e)}
              >
                <BpmnNodeGlyph node={node} />
                {node.type === "serviceTask" ||
                node.type === "userTask" ||
                node.type === "messageTask" ||
                node.type === "callActivity" ||
                node.type === "subProcess" ? (
                  <text
                    x={node.width / 2}
                    y={node.height / 2 + 4}
                    textAnchor="middle"
                    fontSize="11"
                    className="ispf-bpmn-label bpmn-glyph-inner-label"
                  >
                    {node.name || node.id}
                  </text>
                ) : (
                  <text
                    x={node.width / 2}
                    y={node.height + 14}
                    textAnchor="middle"
                    fontSize="12"
                    className="ispf-bpmn-label"
                  >
                    {node.name || node.id}
                  </text>
                )}
              </g>
            );
          })}
        </svg>
      </div>

      <IspfBpmnPropertiesPanel
        selection={
          selection?.kind === "node"
            ? { kind: "node", node: diagram.nodes.find((n) => n.id === selection.node.id) || selection.node }
            : selection?.kind === "edge"
              ? {
                  kind: "edge",
                  edge: diagram.edges.find((e) => e.id === selection.edge.id) || selection.edge,
                }
              : null
        }
        onChangeNode={updateNode}
        onChangeEdge={updateEdge}
        onDelete={deleteSelection}
      />
    </div>
  );
}
