import { describe, expect, it } from "vitest";
import { BpmnModdle } from "bpmn-moddle";
import ispfModdle from "./ispf-moddle.json";

const TASK_XML = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:ispf="http://ispf.io/bpmn"
             targetNamespace="http://ispf.io/bpmn">
  <process id="p" isExecutable="true">
    <startEvent id="start"/>
    <serviceTask id="t" ispf:action="log" ispf:message="hi"/>
    <userTask id="u" ispf:function="fn"/>
    <endEvent id="end"/>
    <sequenceFlow id="f1" sourceRef="start" targetRef="t"/>
    <sequenceFlow id="f2" sourceRef="t" targetRef="u"/>
    <sequenceFlow id="f3" sourceRef="u" targetRef="end"/>
  </process>
</definitions>`;

describe("ispf-moddle", () => {
  it("parses serviceTask and userTask with ISPF extension", async () => {
    const moddle = new BpmnModdle({ ispf: ispfModdle });
    const { rootElement } = await moddle.fromXML(TASK_XML);
    const process = rootElement.rootElements[0];
    const ids = process.flowElements.map((el: { id: string }) => el.id);
    expect(ids).toContain("t");
    expect(ids).toContain("u");
    expect(ids).toContain("f1");
  });
});
