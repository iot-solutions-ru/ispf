import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { unpackFromFile } from "./unpack.mjs";
import { readJsonFile, writeJsonFile } from "./util.mjs";

/**
 * Copies a solution repo (or a bundle.json) into a plant fork that owns its schema,
 * hub namespace, and ui-pack. Upstream files stay mergeable; plant changes are ordinary Git edits.
 */
export function forkSolution(src, dest, appId) {
  if (!appId || !/^[a-z0-9][a-z0-9-]{1,62}$/.test(appId)) {
    throw new Error("fork requires an app id of lowercase letters, digits, and dashes");
  }
  if (fs.existsSync(dest)) {
    throw new Error(`Refusing to overwrite ${dest}`);
  }
  const absSrc = path.resolve(src);
  const absDest = path.resolve(dest);
  if (fs.statSync(absSrc).isFile()) {
    unpackFromFile(absSrc, absDest);
  } else {
    fs.cpSync(absSrc, absDest, { recursive: true });
  }
  const solutionPath = path.join(absDest, "solution.json");
  if (!fs.existsSync(solutionPath)) {
    throw new Error(`fork did not produce solution.json in ${absDest}`);
  }
  const solution = readJsonFile(solutionPath);
  const forkedFrom = {
    schemaName: solution.schemaName ?? null,
    version: solution.version ?? null,
    sha256: crypto.createHash("sha256").update(fs.readFileSync(solutionPath)).digest("hex"),
  };
  solution.schemaName = "app_" + appId.replaceAll("-", "_");
  solution.metadata = {
    ...(solution.metadata ?? {}),
    forkedFrom,
  };
  writeJsonFile(solutionPath, solution);
  return { dest: absDest, schemaName: solution.schemaName, forkedFrom };
}
