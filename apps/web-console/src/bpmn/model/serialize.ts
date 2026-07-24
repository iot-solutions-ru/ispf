import type { FlowEdge, FlowNode, WorkflowDiagram } from "./types";

function esc(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/"/g, "&quot;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

function ispfAttrs(ispf: Record<string, string>): string {
  return Object.entries(ispf)
    .filter(([, v]) => v != null && String(v).trim() !== "")
    .map(([k, v]) => ` ispf:${k}="${esc(String(v))}"`)
    .join("");
}

function nodeXml(node: FlowNode): string {
  const nameAttr = node.name ? ` name="${esc(node.name)}"` : "";
  const attrs = ispfAttrs(node.ispf);

  switch (node.type) {
    case "startEvent":
      return `<startEvent id="${esc(node.id)}"${nameAttr}${attrs}/>`;
    case "endEvent":
      return `<endEvent id="${esc(node.id)}"${nameAttr}${attrs}/>`;
    case "serviceTask":
      return `<serviceTask id="${esc(node.id)}"${nameAttr}${attrs}/>`;
    case "userTask":
      return `<userTask id="${esc(node.id)}"${nameAttr}${attrs}/>`;
    case "messageTask":
      return `<messageTask id="${esc(node.id)}"${nameAttr}${attrs}/>`;
    case "callActivity": {
      const called = node.ispf.workflowPath ? ` calledElement="${esc(node.ispf.workflowPath)}"` : "";
      return `<callActivity id="${esc(node.id)}"${nameAttr}${called}${attrs}/>`;
    }
    case "exclusiveGateway":
      return `<exclusiveGateway id="${esc(node.id)}"${nameAttr}${attrs}/>`;
    case "parallelGateway":
      return `<parallelGateway id="${esc(node.id)}"${nameAttr}${attrs}/>`;
    case "subProcess":
      return `<subProcess id="${esc(node.id)}"${nameAttr}${attrs}>
      <startEvent id="${esc(node.id)}_inner_start"/>
      <endEvent id="${esc(node.id)}_inner_end"/>
      <sequenceFlow id="${esc(node.id)}_inner_f1" sourceRef="${esc(node.id)}_inner_start" targetRef="${esc(node.id)}_inner_end"/>
    </subProcess>`;
    case "intermediateCatchEvent": {
      if (node.catchKind === "message" || node.ispf.message) {
        const msg = node.ispf.message || node.name || "message";
        return `<intermediateCatchEvent id="${esc(node.id)}"${nameAttr}${attrs}>
      <messageEventDefinition messageRef="${esc(msg)}"/>
    </intermediateCatchEvent>`;
      }
      if (node.catchKind === "timer" || node.ispf.durationSeconds) {
        return `<intermediateCatchEvent id="${esc(node.id)}"${nameAttr}${attrs}/>`;
      }
      // signal (default)
      return `<intermediateCatchEvent id="${esc(node.id)}"${nameAttr}${attrs}/>`;
    }
    case "intermediateThrowEvent": {
      const msg = node.ispf.message || node.name || "message";
      return `<intermediateThrowEvent id="${esc(node.id)}"${nameAttr}${attrs}>
      <messageEventDefinition messageRef="${esc(msg)}"/>
    </intermediateThrowEvent>`;
    }
    case "boundaryEvent": {
      const attached = node.attachedToRef ? ` attachedToRef="${esc(node.attachedToRef)}"` : "";
      const cancel =
        node.cancelActivity === false ? ` cancelActivity="false"` : ` cancelActivity="true"`;
      return `<boundaryEvent id="${esc(node.id)}"${nameAttr}${attached}${cancel}${attrs}/>`;
    }
    default:
      return `<!-- unsupported ${node.type} ${esc(node.id)} -->`;
  }
}

function edgeXml(edge: FlowEdge): string {
  const nameAttr = edge.name ? ` name="${esc(edge.name)}"` : "";
  const ispf: string[] = [];
  if (edge.condition?.trim()) ispf.push(`ispf:condition="${esc(edge.condition.trim())}"`);
  if (edge.isDefault) ispf.push(`ispf:default="true"`);
  const extra = ispf.length ? ` ${ispf.join(" ")}` : "";
  return `<sequenceFlow id="${esc(edge.id)}"${nameAttr} sourceRef="${esc(edge.sourceRef)}" targetRef="${esc(edge.targetRef)}"${extra}/>`;
}

function shapeXml(node: FlowNode): string {
  return `<bpmndi:BPMNShape id="${esc(node.id)}_di" bpmnElement="${esc(node.id)}">
        <dc:Bounds x="${Math.round(node.x)}" y="${Math.round(node.y)}" width="${Math.round(node.width)}" height="${Math.round(node.height)}"/>
      </bpmndi:BPMNShape>`;
}

function edgeDiXml(edge: FlowEdge, nodes: FlowNode[]): string {
  const source = nodes.find((n) => n.id === edge.sourceRef);
  const target = nodes.find((n) => n.id === edge.targetRef);
  const x1 = source ? source.x + source.width / 2 : 0;
  const y1 = source ? source.y + source.height / 2 : 0;
  const x2 = target ? target.x + target.width / 2 : 100;
  const y2 = target ? target.y + target.height / 2 : 100;
  return `<bpmndi:BPMNEdge id="${esc(edge.id)}_di" bpmnElement="${esc(edge.id)}">
        <di:waypoint x="${Math.round(x1)}" y="${Math.round(y1)}"/>
        <di:waypoint x="${Math.round(x2)}" y="${Math.round(y2)}"/>
      </bpmndi:BPMNEdge>`;
}

/** Serialize editor document to BPMN 2.0 XML with ISPF extensions and DI. */
export function serializeWorkflowDiagram(diagram: WorkflowDiagram): string {
  const processId = diagram.processId || "Process_1";
  const processName = diagram.processName || "Process";
  const nodesXml = diagram.nodes.map(nodeXml).join("\n    ");
  const edgesXml = diagram.edges.map(edgeXml).join("\n    ");
  const shapes = diagram.nodes.map(shapeXml).join("\n      ");
  const edgeDis = diagram.edges.map((e) => edgeDiXml(e, diagram.nodes)).join("\n      ");

  return `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
             xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
             xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
             xmlns:ispf="http://ispf.io/bpmn"
             targetNamespace="http://ispf.io/bpmn">
  <process id="${esc(processId)}" name="${esc(processName)}" isExecutable="true">
    ${nodesXml}
    ${edgesXml}
  </process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="${esc(processId)}">
      ${shapes}
      ${edgeDis}
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</definitions>
`;
}
