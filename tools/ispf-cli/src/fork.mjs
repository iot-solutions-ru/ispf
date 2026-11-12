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
  const solution = readJsonFile(solutionPath);
  const previousDomain = solution.metadata?.domain ?? null;
  const previousSchema = solution.schemaName ?? null;
  let previousAppId = solution.operatorUi?.appId ?? solution.operatorManifest?.appId ?? null;
  const uiPackPath = path.join(absDest, "ui-pack", "ui-pack.json");
  if (!previousAppId && fs.existsSync(uiPackPath)) {
    const uiPack = readJsonFile(uiPackPath);
    previousAppId = uiPack.appId ?? uiPack.id ?? null;
  }
  if (!previousAppId && typeof previousDomain === "string") {
    previousAppId = previousDomain;
  }
  const forkedFrom = {
    appId: previousAppId,
    schemaName: previousSchema,
    version: solution.version ?? null,
    sha256: crypto.createHash("sha256").update(stableStringify(packDirectory(absDest))).digest("hex"),
  };
  const nextSchema = "app_" + appId.replaceAll("-", "_");
  solution.schemaName = nextSchema;
  if (previousDomain && solution.metadata) {
    solution.metadata.domain = appId;
  }
  rewriteAppIds(solution, previousAppId, appId);
  const hubRenames = renameBlueprintNamespaces(absDest, previousAppId, previousDomain, appId);
  replaceStrings(solution, hubRenames, "kebab");
  if (previousSchema && previousSchema !== nextSchema) {
    replaceStrings(solution, [[previousSchema, nextSchema]], "ident");
  }
  if (previousAppId) {
    replaceStrings(solution, [[`/apps/${previousAppId}/`, `/apps/${appId}/`]], "literal");
  }
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
  replaceTokens(absDest, hubRenames, solutionPath, "kebab");
  if (previousSchema && previousSchema !== nextSchema) {
    replaceTokens(absDest, [[previousSchema, nextSchema]], solutionPath, "ident");
  }
  if (previousAppId) {
    replaceTokens(absDest, [[`/apps/${previousAppId}/`, `/apps/${appId}/`]], solutionPath, "literal");
    rewriteJsonAppIds(absDest, previousAppId, appId, solutionPath);
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

function rewriteAppIds(value, oldAppId, newAppId) {
  if (!oldAppId || oldAppId === newAppId || !value || typeof value !== "object") {
    return;
  }
  if (Array.isArray(value)) {
    for (const entry of value) {
      rewriteAppIds(entry, oldAppId, newAppId);
    }
    return;
  }
  for (const [key, child] of Object.entries(value)) {
    if ((key === "appId" || key === "id" || key === "backendAppId" || key === "operatorAppId") && child === oldAppId) {
      value[key] = newAppId;
    } else if (key === "packId" && child === `${oldAppId}-ui`) {
      value[key] = `${newAppId}-ui`;
    } else if (key === "basePath" && typeof child === "string") {
      const escaped = oldAppId.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
      value[key] = child.replace(new RegExp(`/${escaped}(?=/|$)`, "g"), `/${newAppId}`);
    } else if (key === "externalSpaUrl" && typeof child === "string") {
      value[key] = child.replaceAll(`/apps/${oldAppId}/`, `/apps/${newAppId}/`);
    } else {
      rewriteAppIds(child, oldAppId, newAppId);
    }
  }
}

function renameBlueprintNamespaces(dest, oldAppId, oldDomain, newAppId) {
  const blueprintsDir = path.join(dest, "blueprints");
  if (!fs.existsSync(blueprintsDir)) {
    return [];
  }
  const renames = [];
  for (const file of fs.readdirSync(blueprintsDir)) {
    if (!file.endsWith(".json")) {
      continue;
    }
    const full = path.join(blueprintsDir, file);
    const blueprint = readJsonFile(full);
    if (!blueprint || typeof blueprint.name !== "string") {
      continue;
    }
    const nextName = renamedNamespace(blueprint.name, oldAppId, oldDomain, newAppId);
    if (nextName === blueprint.name) {
      continue;
    }
    const previousName = blueprint.name;
    blueprint.name = nextName;
    const nextFile = path.join(blueprintsDir, `${nextName}.json`);
    if (nextFile !== full && fs.existsSync(nextFile)) {
      throw new Error(`Fork hub rename would overwrite ${nextFile}`);
    }
    writeJsonFile(nextFile, blueprint);
    if (nextFile !== full) {
      fs.rmSync(full);
    }
    renames.push([previousName, nextName]);
  }
  return renames;
}

function renamedNamespace(name, oldAppId, oldDomain, newAppId) {
  const markers = [oldDomain, oldAppId].filter(Boolean).sort((a, b) => b.length - a.length);
  for (const marker of markers) {
    if (name === marker || name.startsWith(`${marker}-`) || name.startsWith(`${marker}.`)) {
      return newAppId + name.slice(marker.length);
    }
  }
  return name;
}

function rewriteJsonAppIds(root, oldAppId, newAppId, skipFile) {
  const stack = [root];
  while (stack.length) {
    const current = stack.pop();
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      if (entry.name === ".git" || entry.name === "node_modules") {
        continue;
      }
      const full = path.join(current, entry.name);
      if (entry.isDirectory()) {
        stack.push(full);
        continue;
      }
      if (!entry.name.endsWith(".json") || (skipFile && path.resolve(full) === path.resolve(skipFile))) {
        continue;
      }
      const json = readJsonFile(full);
      const before = stableStringify(json);
      rewriteAppIds(json, oldAppId, newAppId);
      if (stableStringify(json) !== before) {
        writeJsonFile(full, json);
      }
    }
  }
}

function replaceTokens(root, pairs, skipFile, mode) {
  if (!pairs.length) {
    return;
  }
  const ordered = [...pairs].sort((a, b) => b[0].length - a[0].length);
  const stack = [root];
  while (stack.length) {
    const current = stack.pop();
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      if (entry.name === ".git" || entry.name === "node_modules") {
        continue;
      }
      const full = path.join(current, entry.name);
      if (entry.isDirectory()) {
        stack.push(full);
        continue;
      }
      if (skipFile && path.resolve(full) === path.resolve(skipFile)) {
        continue;
      }
      if (!/\.(json|sql|html|md|ts|tsx|js|mjs)$/i.test(entry.name)) {
        continue;
      }
      const original = fs.readFileSync(full, "utf8");
      const next = applyPairs(original, ordered, mode);
      if (next !== original) {
        fs.writeFileSync(full, next);
      }
    }
  }
}

function replaceStrings(value, pairs, mode) {
  if (!pairs.length || !value || typeof value !== "object") {
    return;
  }
  if (Array.isArray(value)) {
    for (const entry of value) {
      replaceStrings(entry, pairs, mode);
    }
    return;
  }
  for (const [key, child] of Object.entries(value)) {
    if (typeof child === "string") {
      value[key] = applyPairs(child, pairs, mode);
    } else {
      replaceStrings(child, pairs, mode);
    }
  }
}

function applyPairs(text, pairs, mode) {
  const ordered = [...pairs].sort((a, b) => b[0].length - a[0].length);
  let next = text;
  for (const [from, to] of ordered) {
    if (mode === "literal") {
      next = next.replaceAll(from, to);
      continue;
    }
    const escaped = from.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const boundary = mode === "kebab" ? "A-Za-z0-9_-" : "A-Za-z0-9_";
    next = next.replace(new RegExp(`(?<![${boundary}])${escaped}(?![${boundary}])`, "g"), to);
  }
  return next;
}

