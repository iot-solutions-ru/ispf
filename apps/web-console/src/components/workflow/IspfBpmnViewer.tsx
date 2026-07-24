import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { adaptForeignBpmn } from "../../bpmn/adaptForeignBpmn";
import { deserializeWorkflowDiagram } from "../../bpmn/model/deserialize";
import type { FlowNode } from "../../bpmn/model/types";
import BpmnNodeGlyph from "./BpmnNodeGlyph";
import BpmnSequenceFlow from "./BpmnSequenceFlow";

interface Props {
  xml: string;
}

function isActivity(type: FlowNode["type"]): boolean {
  return (
    type === "serviceTask" ||
    type === "userTask" ||
    type === "messageTask" ||
    type === "callActivity" ||
    type === "subProcess"
  );
}

export default function IspfBpmnViewer({ xml }: Props) {
  const { t } = useTranslation("workflow");

  const { diagram, error } = useMemo(() => {
    try {
      return { diagram: deserializeWorkflowDiagram(xml), error: null as string | null };
    } catch (e) {
      try {
        return { diagram: adaptForeignBpmn(xml).diagram, error: null as string | null };
      } catch {
        return { diagram: null, error: (e as Error).message };
      }
    }
  }, [xml]);

  if (error || !diagram) {
    return (
      <div className="bpmn-viewer-fallback">
        <p className="hint error">{t("bpmn.viewerUnavailable", { error: error || "empty" })}</p>
        <pre className="workflow-code-block workflow-bpmn-view">{xml}</pre>
      </div>
    );
  }

  let maxX = 400;
  let maxY = 300;
  for (const n of diagram.nodes) {
    maxX = Math.max(maxX, n.x + n.width + 80);
    maxY = Math.max(maxY, n.y + n.height + 80);
  }

  return (
    <svg className="ispf-bpmn-canvas ispf-bpmn-viewer" viewBox={`0 0 ${maxX} ${maxY}`}>
      <defs>
        <marker
          id="ispf-arrow-view"
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
        return (
          <BpmnSequenceFlow
            key={edge.id}
            edge={edge}
            source={s}
            target={tgt}
            markerId="ispf-arrow-view"
          />
        );
      })}
      {diagram.nodes.map((node) => (
        <g
          key={node.id}
          transform={`translate(${node.x},${node.y})`}
          className={`ispf-bpmn-node type-${node.type}`}
        >
          <BpmnNodeGlyph node={node} />
          {isActivity(node.type) ? (
            <text
              className="ispf-bpmn-label bpmn-glyph-inner-label"
              x={node.width / 2}
              y={node.height / 2 + 4}
              textAnchor="middle"
              fontSize="11"
            >
              {node.name || node.id}
            </text>
          ) : (
            <text
              className="ispf-bpmn-label"
              x={node.width / 2}
              y={node.height + 14}
              textAnchor="middle"
              fontSize="12"
            >
              {node.name || node.id}
            </text>
          )}
        </g>
      ))}
    </svg>
  );
}
