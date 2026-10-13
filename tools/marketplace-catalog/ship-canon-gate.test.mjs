import assert from "node:assert/strict";
import test from "node:test";
import { catalogShipCanonFailures } from "./validate-catalog.mjs";

test("catalog ship canon fails LOGIC_HOST_DEVICE", () => {
  const messages = catalogShipCanonFailures("demo", {
    objects: [{ parentPath: "root.platform.devices", name: "hub", type: "DEVICE" }],
    functions: [{ objectPath: "root.platform.devices.hub", functionName: "tick" }],
  });
  assert.equal(messages.length, 1);
  assert.match(messages[0], /\[LOGIC_HOST_DEVICE\]/);
  assert.match(messages[0], /^demo:/);
});

test("catalog ship canon accepts a singleton hub", () => {
  const messages = catalogShipCanonFailures("demo", {
    objects: [{ parentPath: "root.platform.singleton-blueprints", name: "hub", type: "CUSTOM" }],
    functions: [{ objectPath: "root.platform.singleton-blueprints.hub", functionName: "tick" }],
  });
  assert.deepEqual(messages, []);
});
