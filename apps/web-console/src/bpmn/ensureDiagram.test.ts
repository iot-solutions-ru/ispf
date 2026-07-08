import { describe, expect, it } from "vitest";
import { ensureBpmnDiagram } from "./ensureDiagram";

const MES_DISPATCH_XML = `<?xml version="1.0" encoding="UTF-8"?><definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:ispf="http://ispf.io/bpmn" targetNamespace="http://ispf.io/bpmn"><process id="mes-work-order-dispatch" name="MES Work Order Dispatch" isExecutable="true"><startEvent id="start" name="Start"/><serviceTask id="notifyDispatch" name="Log dispatch" ispf:action="log" ispf:message="mes-platform-production: work order dispatched"/><userTask id="confirmStart" name="Confirm work order start" ispf:title="Confirm work order start" ispf:instructions="Review the dispatched work order and confirm operator acceptance" ispf:assigneeRole="operator" ispf:targetObject="root.platform.devices.mes-platform-production-hub" ispf:function="mes_dispatch_confirmWorkOrder"/><endEvent id="end" name="End"/><sequenceFlow id="f1" sourceRef="start" targetRef="notifyDispatch"/><sequenceFlow id="f2" sourceRef="notifyDispatch" targetRef="confirmStart"/><sequenceFlow id="f3" sourceRef="confirmStart" targetRef="end"/></process></definitions>`;

describe("ensureBpmnDiagram", () => {
  it("adds DI with shapes and edges for headless ISPF BPMN", async () => {
    const laid = await ensureBpmnDiagram(MES_DISPATCH_XML);
    expect(laid).toMatch(/bpmndi:BPMNDiagram/);
    expect((laid.match(/<bpmndi:BPMNShape/g) ?? []).length).toBe(4);
    expect((laid.match(/<bpmndi:BPMNEdge/g) ?? []).length).toBe(3);
    for (const id of ["start", "notifyDispatch", "confirmStart", "end"]) {
      expect(laid).toContain(`bpmnElement="${id}"`);
    }
  });

  it("skips auto-layout when DI is already present", async () => {
    const withDi = await ensureBpmnDiagram(MES_DISPATCH_XML);
    const again = await ensureBpmnDiagram(withDi);
    expect(again.trim()).toBe(withDi.trim());
  });
});
