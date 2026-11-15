import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { catalogShipCanonFailures } from "./validate-catalog.mjs";

const fixturesDir = join(dirname(fileURLToPath(import.meta.url)), "../ship-canon-fixtures");
const expect = JSON.parse(readFileSync(join(fixturesDir, "expect.json"), "utf8"));

function loadFixture(name) {
  return JSON.parse(readFileSync(join(fixturesDir, `${name}.json`), "utf8"));
}

for (const [name, wanted] of Object.entries(expect)) {
  test(`catalog shared fixture ${name}`, () => {
    const messages = catalogShipCanonFailures("fixture", loadFixture(name));
    if (wanted.codes.length === 0) {
      assert.deepEqual(messages, []);
      return;
    }
    assert.equal(messages.length, wanted.codes.length);
    for (const code of wanted.codes) {
      assert.ok(messages.some((m) => m.includes(`[${code}]`)), `missing ${code} in ${messages}`);
    }
  });
}
