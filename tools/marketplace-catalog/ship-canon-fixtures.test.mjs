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
    const codes = [
      ...new Set(
        messages
          .map((m) => {
            const match = /\[([A-Z0-9_]+)\]/.exec(m);
            return match ? match[1] : null;
          })
          .filter(Boolean)
      ),
    ].sort();
    assert.deepEqual(codes, [...wanted.codes].sort());
  });
}
