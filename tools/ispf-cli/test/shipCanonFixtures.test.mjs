import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { applyStrictShipCanon, findLogicHostDeviceIssues } from "../src/shipCanon.mjs";

const fixturesDir = join(dirname(fileURLToPath(import.meta.url)), "../../ship-canon-fixtures");
const expect = JSON.parse(readFileSync(join(fixturesDir, "expect.json"), "utf8"));

function loadFixture(name) {
  return JSON.parse(readFileSync(join(fixturesDir, `${name}.json`), "utf8"));
}

for (const [name, wanted] of Object.entries(expect)) {
  test(`shared fixture ${name}`, () => {
    const bundle = loadFixture(name);
    const issues = findLogicHostDeviceIssues(bundle);
    const codes = [...new Set(issues.map((i) => i.code))];
    assert.deepEqual(codes.sort(), [...wanted.codes].sort());

    const local = { status: "OK", errors: [] };
    const result = applyStrictShipCanon(local, bundle);
    assert.equal(result.status, wanted.strictStatus);
  });
}
