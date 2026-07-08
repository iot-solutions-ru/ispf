import { BpmnModdle } from "bpmn-moddle";
import { layoutProcess } from "bpmn-auto-layout";

const HAS_DIAGRAM = /bpmndi:BPMNDiagram|<BPMNDiagram/i;

type ModdleElement = {
  id?: string;
  $type: string;
  flowElements?: ModdleElement[];
  participants?: Array<{ processRef?: ModdleElement }>;
  sourceRef?: ModdleElement;
  targetRef?: ModdleElement;
  incoming?: ModdleElement[];
  outgoing?: ModdleElement[];
};

type ModdleDefinitions = {
  rootElements?: ModdleElement[];
};

function isSequenceFlow(element: ModdleElement): boolean {
  return element.$type === "bpmn:SequenceFlow";
}

/** bpmn-auto-layout needs incoming/outgoing wired; ISPF XML only has sequenceFlow sourceRef/targetRef. */
function wireSequenceFlows(container: { flowElements?: ModdleElement[] }): void {
  const flowElements = container.flowElements;
  if (!flowElements?.length) return;

  for (const element of flowElements) {
    if (isSequenceFlow(element)) continue;
    element.incoming = [];
    element.outgoing = [];
  }

  for (const element of flowElements) {
    if (!isSequenceFlow(element)) continue;
    const source = element.sourceRef;
    const target = element.targetRef;
    if (!source || !target) continue;
    source.outgoing = [...(source.outgoing ?? []), element];
    target.incoming = [...(target.incoming ?? []), element];
  }

  for (const element of flowElements) {
    if (element.$type === "bpmn:SubProcess") {
      wireSequenceFlows(element);
    }
  }
}

function wireAllContainers(definitions: ModdleDefinitions): void {
  for (const root of definitions.rootElements ?? []) {
    if (root.$type === "bpmn:Process") {
      wireSequenceFlows(root);
      continue;
    }
    if (root.$type === "bpmn:Collaboration") {
      for (const participant of root.participants ?? []) {
        if (participant.processRef) {
          wireSequenceFlows(participant.processRef);
        }
      }
    }
  }
}

async function wireXmlForAutoLayout(xml: string): Promise<string> {
  const moddle = new BpmnModdle();
  const { rootElement } = await moddle.fromXML(xml);
  wireAllContainers(rootElement);
  const { xml: wired } = await moddle.toXML(rootElement, { format: true });
  return wired;
}

/** BPMN from the engine often lacks DI (bpmndi); bpmn-js requires it to render. */
export async function ensureBpmnDiagram(xml: string): Promise<string> {
  const content = xml.trim();
  if (!content || HAS_DIAGRAM.test(content)) {
    return content;
  }
  const wired = await wireXmlForAutoLayout(content);
  return layoutProcess(wired);
}
