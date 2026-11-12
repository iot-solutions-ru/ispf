import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { packDirectory } from "./pack.mjs";
import { unpackFromFile } from "./unpack.mjs";
import { readJsonFile, stableStringify, writeJsonFile } from "./util.mjs";

const APP_ID = /^[a-z0-9][a-z0-9-]{0,61}[a-z0-9]$/;

/**
 * Copies a solution repo (or a bundle.json) into a plant fork that owns its schema.
 * Upstream files stay mergeable; plant changes are ordinary Git edits.
 */
export function forkSolution(src, dest, appId) {
  if (!appId || !APP_ID.test(appId)) {
    throw new Error("fork requires an app id of lowercase letters, digits, and dashes");
  }
  const absSrc = path.resolve(src);
  const absDest = path.resolve(dest);
  if (fs.lstatSync(absDest, { throwIfNoEntry: false })) {
    throw new Error(`Refusing to overwrite ${dest}`);
  }
  if (absDest === absSrc || absDest.startsWith(absSrc + path.sep)) {
    throw new Error(`Refusing to fork ${src} into itself`);
  }
  const srcStat = fs.statSync(absSrc);
  if (srcStat.isFile()) {
    unpackFromFile(absSrc, absDest);
  } else if (fs.existsSync(path.join(absSrc, "solution.json"))) {
    fs.cpSync(absSrc, absDest, {
      recursive: true,
      filter: (source) => {
        const base = path.basename(source);
        return base !== ".git" && base !== "node_modules" && base !== "bundle.json";
      },
    });
  } else if (fs.existsSync(path.join(absSrc, "bundle.json"))) {
    unpackFromFile(path.join(absSrc, "bundle.json"), absDest);
  } else {
    throw new Error(`fork source has neither solution.json nor bundle.json: ${src}`);
  }
  const solutionPath = path.join(absDest, "solution.json");
  if (!fs.existsSync(solutionPath)) {
    throw new Error(`fork did not produce solution.json in ${absDest}`);
  }
  const forkedFrom = {
    schemaName: null,
    version: null,
    sha256: crypto.createHash("sha256").update(stableStringify(packDirectory(absDest))).digest("hex"),
  };
  const solution = readJsonFile(solutionPath);
  forkedFrom.schemaName = solution.schemaName ?? null;
  forkedFrom.version = solution.version ?? null;
  solution.schemaName = "app_" + appId.replaceAll("-", "_");
  solution.metadata = {
    ...(solution.metadata ?? {}),
    forkedFrom,
  };
  const displayName = titleFromAppId(appId);
  if (solution.displayName != null) {
    solution.displayName = displayName;
  }
  writeJsonFile(solutionPath, solution);
  const bundlePath = path.join(absDest, "bundle.json");
  if (fs.existsSync(bundlePath)) {
    const bundle = readJsonFile(bundlePath);
    bundle.displayName = displayName;
    bundle.schemaName = solution.schemaName;
    if (bundle.operatorUi && typeof bundle.operatorUi === "object") {
      bundle.operatorUi = { ...bundle.operatorUi, appId };
    }
    writeJsonFile(bundlePath, bundle);
  }
  return { dest: absDest, schemaName: solution.schemaName, forkedFrom };
}

function titleFromAppId(appId) {
  return appId
    .split("-")
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}
