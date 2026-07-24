/**
 * @vitest-environment jsdom
 */
import { describe, expect, it } from "vitest";
import { adaptForeignBpmn } from "../adaptForeignBpmn";
import { deserializeWorkflowDiagram } from "./deserialize";
import { serializeWorkflowDiagram } from "./serialize";
import { createEmptyDiagram } from "./types";

const SAMPLE = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:ispf="http://ispf.io/bpmn"
             xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
             xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
             targetNamespace="http://ispf.io/bpmn">
  <process id="p1" name="Demo" isExecutable="true">
    <startEvent id="start" name="Start"/>
    <serviceTask id="t1" name="Log" ispf:action="log" ispf:message="hi"/>
    <endEvent id="end" name="End"/>
    <sequenceFlow id="f1" sourceRef="start" targetRef="t1"/>
    <sequenceFlow id="f2" sourceRef="t1" targetRef="end"/>
  </process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="p1">
      <bpmndi:BPMNShape id="start_di" bpmnElement="start">
        <dc:Bounds x="100" y="100" width="36" height="36"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="t1_di" bpmnElement="t1">
        <dc:Bounds x="200" y="80" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="end_di" bpmnElement="end">
        <dc:Bounds x="360" y="100" width="36" height="36"/>
      </bpmndi:BPMNShape>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</definitions>`;

describe("workflow diagram model", () => {
  it("round-trips serviceTask ispf attrs", () => {
    const diagram = deserializeWorkflowDiagram(SAMPLE);
    expect(diagram.nodes.find((n) => n.id === "t1")?.ispf.action).toBe("log");
    expect(diagram.edges).toHaveLength(2);
    const xml = serializeWorkflowDiagram(diagram);
    expect(xml).toContain('ispf:action="log"');
    expect(xml).toContain("bpmndi:BPMNShape");
    const again = deserializeWorkflowDiagram(xml);
    expect(again.nodes.find((n) => n.id === "t1")?.ispf.message).toBe("hi");
  });

  it("creates empty diagram with start", () => {
    const d = createEmptyDiagram();
    expect(d.nodes[0]?.type).toBe("startEvent");
  });

  it("keeps callActivity and sets workflowPath from calledElement", () => {
    const foreign = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:camunda="http://camunda.org/schema/1.0/bpmn" targetNamespace="t">
  <process id="p" isExecutable="true">
    <startEvent id="s"/>
    <callActivity id="c" name="Child" calledElement="other-flow"/>
    <endEvent id="e"/>
    <sequenceFlow id="f1" sourceRef="s" targetRef="c"/>
    <sequenceFlow id="f2" sourceRef="c" targetRef="e"/>
  </process>
</definitions>`;
    const result = adaptForeignBpmn(foreign);
    expect(result.warnings.some((w) => w.code === "map.callActivity")).toBe(true);
    const task = result.diagram.nodes.find((n) => n.id === "c");
    expect(task?.type).toBe("callActivity");
    expect(task?.ispf.workflowPath).toBe("root.platform.workflows.other-flow");
    expect(result.xml).toContain("callActivity");
    expect(result.xml).toContain('ispf:workflowPath="root.platform.workflows.other-flow"');
  });

  it("round-trips callActivity", () => {
    const xml = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:ispf="http://ispf.io/bpmn" targetNamespace="http://ispf.io/bpmn">
  <process id="p" name="P" isExecutable="true">
    <startEvent id="s"/>
    <callActivity id="c" name="Child" calledElement="root.platform.workflows.child" ispf:workflowPath="root.platform.workflows.child"/>
    <endEvent id="e"/>
    <sequenceFlow id="f1" sourceRef="s" targetRef="c"/>
    <sequenceFlow id="f2" sourceRef="c" targetRef="e"/>
  </process>
</definitions>`;
    const diagram = deserializeWorkflowDiagram(xml);
    const node = diagram.nodes.find((n) => n.id === "c");
    expect(node?.type).toBe("callActivity");
    expect(node?.ispf.workflowPath).toBe("root.platform.workflows.child");
    const out = serializeWorkflowDiagram(diagram);
    expect(out).toContain("callActivity");
    expect(out).toContain("ispf:workflowPath");
  });
});
