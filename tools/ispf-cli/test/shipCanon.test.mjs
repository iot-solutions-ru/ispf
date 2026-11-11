import assert from "node:assert/strict";
import test from "node:test";
import { applyStrictShipCanon, findLogicHostDeviceIssues } from "../src/shipCanon.mjs";

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

test("telemetry MIXIN on DEVICE with functions is allowed", () => {
  const issues = findLogicHostDeviceIssues({
    blueprints: [
      { name: "sensor-v1", type: "MIXIN", targetObjectType: "DEVICE", functions: [{ name: "ack" }] },
    ],
  });
  assert.equal(issues.length, 0);
});
