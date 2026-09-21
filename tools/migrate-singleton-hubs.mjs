#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1")), "..");

function walk(dir, pred, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const f = path.join(dir, e.name);
    if (e.isDirectory()) {
      if (["node_modules", "dist", "build", ".git"].includes(e.name)) continue;
      walk(f, pred, out);
    } else if (pred(e.name)) out.push(f);
  }
  return out;
}

function appIdFromFile(file, bundle) {
  const base = path.basename(file);
  if (base.endsWith("-bundle.json")) return base.replace(/-bundle\.json$/, "");
  const parent = path.basename(path.dirname(file));
  if (parent && parent !== "examples" && parent !== "marketplace-catalog") return parent.replace(/-ui$/, "");
  return (bundle.schemaName || "app").replace(/^app_/, "").replace(/_/g, "-");
}

function isHubObject(obj, fnHosts) {
  if (!obj?.name || !obj?.parentPath) return false;
  const full = `${obj.parentPath}.${obj.name}`;
  const type = String(obj.type || "").toUpperCase();
  if (type !== "DEVICE" && type !== "CUSTOM") return false;
  if (/hub/i.test(obj.name)) return true;
  return fnHosts.has(full) && type === "DEVICE";
}

function migrate(bundle, appId) {
  const hubName = `${appId}-hub-v1`;
  const singletonPath = `root.platform.singleton-blueprints.${hubName}`;
  const fnHosts = new Set((bundle.functions || []).map((f) => f.objectPath).filter(Boolean));
  let changed = false;
  const needsRedirect = [...fnHosts].some((p) => p && !p.startsWith("root.platform.singleton-blueprints."));
  if (!needsRedirect && !(bundle.functions || []).length) return { changed: false, singletonPath };

  if (Array.isArray(bundle.objects)) {
    const before = bundle.objects.length;
    bundle.objects = bundle.objects.filter((o) => !isHubObject(o, fnHosts));
    if (bundle.objects.length !== before) changed = true;
  }
  for (const fn of bundle.functions || []) {
    if (fn.objectPath && !fn.objectPath.startsWith("root.platform.singleton-blueprints.")) {
      fn.objectPath = singletonPath;
      changed = true;
    }
  }
  if (!Array.isArray(bundle.blueprints)) bundle.blueprints = [];
  // Promote existing *-hub-v1 INSTANCE to SINGLETON or add empty SINGLETON
  let hasSingleton = false;
  for (const bp of bundle.blueprints) {
    if (bp && /hub-v1$/i.test(bp.name || "")) {
      if (String(bp.type).toUpperCase() !== "SINGLETON") {
        bp.type = "SINGLETON";
        changed = true;
      }
      hasSingleton = true;
    }
  }
  if (!hasSingleton && (bundle.functions || []).length) {
    bundle.blueprints.unshift({
      name: hubName,
      description: `${bundle.displayName || appId} logic hub (SINGLETON)`,
      type: "SINGLETON",
      variables: [],
      events: [],
      functions: [],
      bindings: [],
      parameters: {},
    });
    changed = true;
  } else if (hasSingleton) {
    // Redirect functions to the richest hub blueprint name
    const hubBp = bundle.blueprints.find((b) => /hub-v1$/i.test(b?.name || "") && String(b.type).toUpperCase() === "SINGLETON");
    if (hubBp) {
      const path = `root.platform.singleton-blueprints.${hubBp.name}`;
      for (const fn of bundle.functions || []) {
        if (fn.objectPath !== path) {
          fn.objectPath = path;
          changed = true;
        }
      }
      return { changed, singletonPath: path };
    }
  }
  return { changed, singletonPath };
}

const targets = [
  ...walk(path.join(ROOT, "examples"), (n) => n === "bundle.json"),
  ...walk(path.join(ROOT, "packages", "ispf-server", "src", "test", "resources"), (n) => n.endsWith("-bundle.json")),
];

let updated = 0;
for (const file of targets) {
  let bundle;
  try {
    bundle = JSON.parse(fs.readFileSync(file, "utf8"));
  } catch {
    continue;
  }
  const appId = appIdFromFile(file, bundle);
  const result = migrate(bundle, appId);
  if (!result.changed) continue;
  fs.writeFileSync(file, JSON.stringify(bundle, null, 2) + "\n");
  updated++;
  console.log("FIX", path.relative(ROOT, file), "->", result.singletonPath);
}
console.log(`Updated ${updated}/${targets.length}`);
