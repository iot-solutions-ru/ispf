import {
  NODE_DEFAULT_SIZE,
  createEmptyDiagram,
  type CatchKind,
  type FlowEdge,
  type FlowNode,
  type FlowNodeType,
  type IspfProps,
  type WorkflowDiagram,
} from "./types";

const ISPF_NS = "http://ispf.io/bpmn";
const BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";
const BPMNDI_NS = "http://www.omg.org/spec/BPMN/20100524/DI";
const DC_NS = "http://www.omg.org/spec/DD/20100524/DC";

const ISPF_ATTR_NAMES = [
  "action",
  "targetObject",
  "variable",
  "value",
  "message",
  "subject",
  "channel",
  "level",
  "assigneeRole",
  "title",
  "instructions",
  "function",
  "condition",
  "signal",
  "objectPath",
  "eventName",
  "payloadJson",
  "contextKey",
  "workflowPath",
  "inputJson",
  "promptTemplate",
  "outputVariable",
  "outputFormat",
  "modelRef",
  "timeoutMs",
  "goalTemplate",
  "agentMode",
  "toolAllowlist",
  "maxSteps",
  "retryable",
  "sourceVariable",
  "functionName",
  "inputMap",
  "outputMap",
  "durationSeconds",
  "default",
] as const;

function localName(el: Element): string {
  return el.localName || el.nodeName.replace(/^.*:/, "");
}

function childrenByLocal(parent: Element, name: string): Element[] {
  return Array.from(parent.children).filter((c) => localName(c) === name);
}

function firstChildByLocal(parent: Element, name: string): Element | undefined {
  return childrenByLocal(parent, name)[0];
}

function readIspf(el: Element): IspfProps {
  const out: IspfProps = {};
  for (const key of ISPF_ATTR_NAMES) {
    const v =
      el.getAttributeNS(ISPF_NS, key) ||
      el.getAttribute(`ispf:${key}`) ||
      el.getAttribute(key);
    if (v != null && v !== "") out[key] = v;
  }
  // Also pick up any other ispf:* attrs present
  for (const attr of Array.from(el.attributes)) {
    const name = attr.name;
    if (name.startsWith("ispf:")) {
      const key = name.slice(5);
      if (!out[key]) out[key] = attr.value;
    } else if (attr.namespaceURI === ISPF_NS && !out[attr.localName]) {
      out[attr.localName] = attr.value;
    }
  }
  return out;
}

function mapType(tag: string): FlowNodeType | null {
  switch (tag) {
    case "startEvent":
    case "endEvent":
    case "serviceTask":
    case "userTask":
    case "messageTask":
    case "callActivity":
    case "exclusiveGateway":
    case "parallelGateway":
    case "intermediateCatchEvent":
    case "intermediateThrowEvent":
    case "boundaryEvent":
    case "subProcess":
      return tag;
    case "task":
      return "serviceTask";
    default:
      return null;
  }
}

function inferCatchKind(el: Element, ispf: IspfProps): CatchKind {
  if (ispf.durationSeconds || firstChildByLocal(el, "timerEventDefinition")) return "timer";
  if (firstChildByLocal(el, "messageEventDefinition") || ispf.message) return "message";
  return "signal";
}

function readMessageRef(el: Element): string | undefined {
  const def = firstChildByLocal(el, "messageEventDefinition");
  if (!def) return undefined;
  return def.getAttribute("messageRef") || undefined;
}

interface Bounds {
  x: number;
  y: number;
  width: number;
  height: number;
}

function parseBoundsMap(doc: Document): Map<string, Bounds> {
  const map = new Map<string, Bounds>();
  const shapes = Array.from(doc.getElementsByTagNameNS(BPMNDI_NS, "BPMNShape")).concat(
    Array.from(doc.getElementsByTagName("bpmndi:BPMNShape") as HTMLCollectionOf<Element>),
    Array.from(doc.getElementsByTagName("BPMNShape") as HTMLCollectionOf<Element>)
  );
  const seen = new Set<Element>();
  for (const shape of shapes) {
    if (seen.has(shape)) continue;
    seen.add(shape);
    const bpmnElement = shape.getAttribute("bpmnElement");
    if (!bpmnElement) continue;
    const bounds =
      firstChildByLocal(shape, "Bounds") ||
      shape.getElementsByTagNameNS(DC_NS, "Bounds")[0] ||
      shape.getElementsByTagName("dc:Bounds")[0];
    if (!bounds) continue;
    map.set(bpmnElement, {
      x: Number(bounds.getAttribute("x") || 0),
      y: Number(bounds.getAttribute("y") || 0),
      width: Number(bounds.getAttribute("width") || 0),
      height: Number(bounds.getAttribute("height") || 0),
    });
  }
  return map;
}

function autoLayout(nodes: FlowNode[]): void {
  const gapX = 160;
  const baseY = 120;
  nodes.forEach((node, index) => {
    if (node.x === 0 && node.y === 0) {
      node.x = 80 + index * gapX;
      node.y = baseY;
    }
  });
}

function findProcess(doc: Document): Element | null {
  const byNs = doc.getElementsByTagNameNS(BPMN_NS, "process");
  if (byNs.length) return byNs[0];
  const plain = doc.getElementsByTagName("process");
  if (plain.length) return plain[0];
  // collaboration → first processRef
  const participants = doc.getElementsByTagName("participant");
  for (const p of Array.from(participants)) {
    const ref = p.getAttribute("processRef");
    if (!ref) continue;
    const all = Array.from(doc.getElementsByTagName("process"));
    const match = all.find((el) => el.getAttribute("id") === ref);
    if (match) return match;
  }
  return null;
}

/** Parse BPMN XML into the editor document model. */
export function deserializeWorkflowDiagram(xml: string): WorkflowDiagram {
  const trimmed = xml?.trim();
  if (!trimmed) return createEmptyDiagram();

  const doc = new DOMParser().parseFromString(trimmed, "application/xml");
  const parseError = doc.querySelector("parsererror");
  if (parseError) {
    throw new Error(parseError.textContent?.trim() || "Invalid BPMN XML");
  }

  const process = findProcess(doc);
  if (!process) {
    throw new Error("BPMN process element not found");
  }

  const boundsMap = parseBoundsMap(doc);
  const nodes: FlowNode[] = [];
  const edges: FlowEdge[] = [];

  for (const child of Array.from(process.children)) {
    const tag = localName(child);
    if (tag === "sequenceFlow") {
      const id = child.getAttribute("id") || `Flow_${edges.length + 1}`;
      const ispf = readIspf(child);
      edges.push({
        id,
        sourceRef: child.getAttribute("sourceRef") || "",
        targetRef: child.getAttribute("targetRef") || "",
        name: child.getAttribute("name") || undefined,
        condition: ispf.condition || undefined,
        isDefault: ispf.default === "true",
      });
      continue;
    }

    const type = mapType(tag);
    if (!type) continue;

    const id = child.getAttribute("id");
    if (!id) continue;
    const ispf = readIspf(child);
    const size = NODE_DEFAULT_SIZE[type];
    const b = boundsMap.get(id);
    const node: FlowNode = {
      id,
      type,
      name: child.getAttribute("name") || "",
      x: b?.x ?? 0,
      y: b?.y ?? 0,
      width: b?.width && b.width > 0 ? b.width : size.width,
      height: b?.height && b.height > 0 ? b.height : size.height,
      ispf,
    };

    if (type === "intermediateCatchEvent" || type === "boundaryEvent") {
      node.catchKind = inferCatchKind(child, ispf);
      const msgRef = readMessageRef(child);
      if (msgRef && !node.ispf.message) node.ispf.message = msgRef;
    }
    if (type === "intermediateThrowEvent") {
      const msgRef = readMessageRef(child);
      if (msgRef && !node.ispf.message) node.ispf.message = msgRef;
    }
    if (type === "boundaryEvent") {
      node.attachedToRef = child.getAttribute("attachedToRef") || undefined;
      node.cancelActivity = child.getAttribute("cancelActivity") !== "false";
    }
    if (type === "callActivity" && !node.ispf.workflowPath) {
      const called = child.getAttribute("calledElement");
      if (called) {
        node.ispf.workflowPath = called.includes(".")
          ? called
          : `root.platform.workflows.${called}`;
      }
    }

    nodes.push(node);
  }

  if (!nodes.some((n) => n.x !== 0 || n.y !== 0)) {
    autoLayout(nodes);
  } else {
    // Fill missing bounds for nodes without DI
    let i = 0;
    for (const node of nodes) {
      if (!boundsMap.has(node.id)) {
        node.x = 80 + i * 160;
        node.y = 220;
        i += 1;
      }
    }
  }

  return {
    processId: process.getAttribute("id") || "Process_1",
    processName: process.getAttribute("name") || "Process",
    nodes,
    edges,
  };
}
