/** ISPF in-memory workflow diagram (editor document). Serializes to BPMN 2.0 + ispf:*. */

export type FlowNodeType =
  | "startEvent"
  | "endEvent"
  | "serviceTask"
  | "userTask"
  | "messageTask"
  | "callActivity"
  | "exclusiveGateway"
  | "parallelGateway"
  | "intermediateCatchEvent"
  | "intermediateThrowEvent"
  | "boundaryEvent"
  | "subProcess";

export type CatchKind = "timer" | "signal" | "message";

export type IspfProps = Record<string, string>;

export interface FlowNode {
  id: string;
  type: FlowNodeType;
  name: string;
  x: number;
  y: number;
  width: number;
  height: number;
  /** ISPF extension attributes (action, message, signal, …) without ispf: prefix. */
  ispf: IspfProps;
  /** intermediateCatchEvent / boundaryEvent */
  catchKind?: CatchKind;
  /** boundaryEvent */
  attachedToRef?: string;
  cancelActivity?: boolean;
}

export interface FlowEdge {
  id: string;
  sourceRef: string;
  targetRef: string;
  name?: string;
  condition?: string;
  isDefault?: boolean;
}

export interface WorkflowDiagram {
  processId: string;
  processName: string;
  nodes: FlowNode[];
  edges: FlowEdge[];
}

export const NODE_DEFAULT_SIZE: Record<FlowNodeType, { width: number; height: number }> = {
  startEvent: { width: 36, height: 36 },
  endEvent: { width: 36, height: 36 },
  serviceTask: { width: 100, height: 80 },
  userTask: { width: 100, height: 80 },
  messageTask: { width: 100, height: 80 },
  callActivity: { width: 100, height: 80 },
  exclusiveGateway: { width: 50, height: 50 },
  parallelGateway: { width: 50, height: 50 },
  intermediateCatchEvent: { width: 36, height: 36 },
  intermediateThrowEvent: { width: 36, height: 36 },
  boundaryEvent: { width: 36, height: 36 },
  subProcess: { width: 120, height: 90 },
};

export function newNodeId(prefix: string): string {
  return `${prefix}_${Math.random().toString(36).slice(2, 9)}`;
}

export function createEmptyDiagram(): WorkflowDiagram {
  const startId = "StartEvent_1";
  return {
    processId: "Process_1",
    processName: "New Process",
    nodes: [
      {
        id: startId,
        type: "startEvent",
        name: "Start",
        x: 180,
        y: 100,
        ...NODE_DEFAULT_SIZE.startEvent,
        ispf: {},
      },
    ],
    edges: [],
  };
}
