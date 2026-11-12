import fs from "node:fs";
import path from "node:path";
import { writeJsonFile } from "./util.mjs";

const APP_ID = /^[a-z0-9][a-z0-9-]{0,61}[a-z0-9]$/;

/**
 * Canonical box: SINGLETON hub, empty sql/seeds/functions, and a ui-pack stub.
 */
export function initSolution(dest, appId, displayName) {
  if (!appId || !APP_ID.test(appId)) {
    throw new Error("init-solution requires an app id of lowercase letters, digits, and dashes");
  }
  if (fs.existsSync(dest)) {
    throw new Error(`Refusing to overwrite ${dest}`);
  }
  const schemaName = "app_" + appId.replaceAll("-", "_");
  fs.mkdirSync(path.join(dest, "sql"), { recursive: true });
  fs.mkdirSync(path.join(dest, "seeds"), { recursive: true });
  fs.mkdirSync(path.join(dest, "functions"), { recursive: true });
  fs.mkdirSync(path.join(dest, "ui-pack"), { recursive: true });
  writeJsonFile(path.join(dest, "solution.json"), {
    version: "0.1.0",
    displayName: displayName || appId,
    schemaName,
    metadata: { domain: appId },
  });
  writeJsonFile(path.join(dest, "blueprints", appId + "-hub.json"), {
    name: appId + "-hub",
    description: "Solution orchestrator",
    type: "SINGLETON",
    parameters: {},
    variables: [],
    functions: [],
    events: [],
    bindings: [],
  });
  fs.writeFileSync(path.join(dest, "sql", "001_init.sql"), "-- plant migrations go here\n", "utf8");
  fs.writeFileSync(path.join(dest, "ui-pack", "README.md"), "# " + (displayName || appId) + "\n", "utf8");
  return { dest: path.resolve(dest), schemaName };
}
