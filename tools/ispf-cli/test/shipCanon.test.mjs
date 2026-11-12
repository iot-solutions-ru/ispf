import assert from "node:assert/strict";
import test from "node:test";
import {
  applyStrictShipCanon,
  extractBpmnInvokeFunctionRefs,
  findLogicHostDeviceIssues,
} from "../src/shipCanon.mjs";

test("findLogicHostDeviceIssues flags function on DEVICE object", () => {
  const issues = findLogicHostDeviceIssues({
    objects: [{ parentPath: "root.platform.devices", name: "hub", type: "DEVICE" }],
    functions: [{ objectPath: "root.platform.devices.hub", functionName: "tick" }],
  });
  assert.equal(issues.length, 1);
  assert.equal(issues[0].code, "LOGIC_HOST_DEVICE");
});

test("findLogicHostDeviceIssues flags SINGLETON targetObjectType=DEVICE", () => {
  const issues = findLogicHostDeviceIssues({
    blueprints: [{ name: "hub-v1", type: "SINGLETON", targetObjectType: "DEVICE" }],
  });
  assert.equal(issues.length, 1);
  assert.equal(issues[0].code, "LOGIC_HOST_DEVICE");
});

test("applyStrictShipCanon promotes to ERROR", () => {
  const local = { status: "OK", errors: [] };
  const result = applyStrictShipCanon(local, {
    objects: [{ parentPath: "root.platform.devices", name: "hub", type: "DEVICE" }],
    functions: [{ objectPath: "root.platform.devices.hub", functionName: "tick" }],
  });
  assert.equal(result.status, "ERROR");
  assert.ok(result.errors.some((e) => e.includes("LOGIC_HOST_DEVICE")));
});

test("clean singleton hub has no ship-canon issues", () => {
  const issues = findLogicHostDeviceIssues({
    objects: [{ parentPath: "root.platform.singleton-blueprints", name: "hub-v1", type: "CUSTOM" }],
    functions: [{ objectPath: "root.platform.singleton-blueprints.hub-v1", functionName: "tick" }],
  });
  assert.equal(issues.length, 0);
});

test("findLogicHostDeviceIssues flags INSTANCE targetObjectType=DEVICE with functions", () => {
  const issues = findLogicHostDeviceIssues({
    blueprints: [
      { name: "twin-v1", type: "INSTANCE", targetObjectType: "DEVICE", functions: [{ name: "tick" }] },
    ],
  });
  assert.equal(issues.length, 1);
  assert.equal(issues[0].code, "LOGIC_HOST_DEVICE");
});

test("findLogicHostDeviceIssues flags split-brain dashboard invoke", () => {
  const issues = findLogicHostDeviceIssues({
    objects: [{ parentPath: "root.platform.devices", name: "hub", type: "DEVICE" }],
    functions: [{ objectPath: "root.platform.singleton-blueprints.hub-v1", functionName: "tick" }],
    dashboards: [
      {
        layoutJson: JSON.stringify({
          widgets: [
            { type: "function-form", objectPath: "root.platform.devices.hub", functionName: "tick" },
          ],
        }),
      },
    ],
  });
  assert.equal(issues.length, 1);
  assert.equal(issues[0].path, "dashboards[0].widgets[0].objectPath");
});

test("extractBpmnInvokeFunctionRefs reads attributes inside each serviceTask", () => {
  const bpmn = `<serviceTask id="t1" ispf:action="invoke_function" ispf:objectPath="root.platform.devices.a" ispf:functionName="f1"/>
<serviceTask id="t2" ispf:functionName="f2" ispf:action="invoke_function" ispf:objectPath="root.platform.devices.b"/>`;
  const refs = extractBpmnInvokeFunctionRefs(bpmn);
  assert.deepEqual(refs, [
    { objectPath: "root.platform.devices.a", functionName: "f1" },
    { objectPath: "root.platform.devices.b", functionName: "f2" },
  ]);
});

test("findLogicHostDeviceIssues flags BPMN with reversed ispf attributes", () => {
  const bpmn = `<serviceTask id="t1" ispf:functionName="tick" ispf:action="invoke_function" ispf:objectPath="root.platform.devices.hub"/>`;
  const issues = findLogicHostDeviceIssues({
    objects: [{ parentPath: "root.platform.devices", name: "hub", type: "DEVICE" }],
    functions: [{ objectPath: "root.platform.singleton-blueprints.good-hub", functionName: "tick" }],
    workflows: [{ bpmnXml: bpmn }],
  });
  assert.equal(issues.length, 1);
  assert.equal(issues[0].path, "workflows[0].bpmnXml.serviceTask[0].objectPath");
});

test("adjacent BPMN tasks do not cross-match invoke attributes", () => {
  const bpmn = `<serviceTask id="t1" ispf:action="invoke_function" ispf:objectPath="root.platform.devices.hub" ispf:functionName="tick"/>
<serviceTask id="t2" ispf:action="invoke_function" ispf:objectPath="root.platform.singleton-blueprints.good-hub" ispf:functionName="other"/>`;
  const issues = findLogicHostDeviceIssues({
    objects: [{ parentPath: "root.platform.devices", name: "hub", type: "DEVICE" }],
    functions: [{ objectPath: "root.platform.singleton-blueprints.good-hub", functionName: "tick" }],
    workflows: [{ bpmnXml: bpmn }],
  });
  assert.equal(issues.length, 1);
  assert.ok(issues[0].message.includes("tick"));
  assert.ok(issues[0].message.includes("root.platform.devices.hub"));
});

test("telemetry MIXIN on DEVICE with functions is allowed", () => {
  const issues = findLogicHostDeviceIssues({
    blueprints: [
      { name: "sensor-v1", type: "MIXIN", targetObjectType: "DEVICE", functions: [{ name: "ack" }] },
    ],
  });
  assert.equal(issues.length, 0);
});
