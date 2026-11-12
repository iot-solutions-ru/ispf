import assert from "node:assert/strict";
import test from "node:test";
import { assertHonestDeployStatus } from "../src/honestDeploy.mjs";

test("assertHonestDeployStatus accepts OK", () => {
  assert.doesNotThrow(() => assertHonestDeployStatus({ status: "OK", version: "1.0.0" }));
  assert.doesNotThrow(() => assertHonestDeployStatus({ version: "1.0.0" }));
});

test("assertHonestDeployStatus rejects PARTIAL and FAILED", () => {
  assert.throws(
    () => assertHonestDeployStatus({ status: "PARTIAL", errors: ["applicationSync: boom"] }),
    (err) => err.message === "deploy PARTIAL"
  );
  assert.throws(
    () => assertHonestDeployStatus({ status: "FAILED", errors: ["register: boom"] }),
    (err) => err.message === "deploy FAILED"
  );
});
