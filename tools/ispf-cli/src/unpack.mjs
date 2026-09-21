import fs from "node:fs";
import path from "node:path";
import {
  externalizeSqlField,
  readJsonFile,
  writeJsonFile,
  writeTextFile,
} from "./util.mjs";

const SOLUTION_ROOT_KEYS = new Set([
  "version",
  "displayName",
  "tablePrefix",
  "schemaName",
  "operatorUi",
  "operatorManifest",
  "metadata",
  "license",
  "requires",
]);

export function unpackBundle(manifest, outDir) {
  fs.mkdirSync(outDir, { recursive: true });
  if (fs.existsSync(path.join(outDir, "solution.json"))) {
    throw new Error(`Refusing to overwrite existing solution.json in ${outDir}`);
  }

  const solution = {};
  for (const key of SOLUTION_ROOT_KEYS) {
    if (manifest[key] !== undefined) {
      solution[key] = manifest[key];
    }
  }
  writeJsonFile(path.join(outDir, "solution.json"), solution);

  if (Array.isArray(manifest.migrations) && manifest.migrations.length) {
    manifest.migrations.forEach((m, i) => {
      const id = m.id ?? `migration_${i + 1}`;
      writeTextFile(path.join(outDir, "sql", `V${i + 1}__${id}.sql`), m.sql ?? "");
    });
  }

  if (Array.isArray(manifest.functions)) {
    const functionsDir = path.join(outDir, "functions");
    for (const fn of manifest.functions) {
      const name = fn.functionName ?? fn.name ?? "function";
      const externalized = externalizeFunctionSteps(fn, functionsDir, name);
      writeJsonFile(path.join(functionsDir, `${name}.json`), externalized);
    }
  }

  if (Array.isArray(manifest.blueprints)) {
    for (const bp of manifest.blueprints) {
      const name = bp.name ?? "blueprint";
      writeJsonFile(path.join(outDir, "blueprints", `${name}.json`), bp);
    }
  }

  if (Array.isArray(manifest.objects) && manifest.objects.length) {
    writeJsonFile(path.join(outDir, "objects.json"), { objects: manifest.objects });
  }

  if (Array.isArray(manifest.bindings) && manifest.bindings.length) {
    writeJsonFile(path.join(outDir, "bindings.json"), { bindings: manifest.bindings });
  }

  if (Array.isArray(manifest.dashboards)) {
    for (const dash of manifest.dashboards) {
      const stem = dashboardStem(dash);
      if (dash.layoutJson) {
        let layout;
        try {
          layout = JSON.parse(dash.layoutJson);
        } catch {
          layout = { _rawLayoutJson: dash.layoutJson };
        }
        writeJsonFile(path.join(outDir, "dashboards", `${stem}.layout.json`), layout);
      }
      const meta = {};
      if (dash.path) meta.path = dash.path;
      if (dash.title) meta.title = dash.title;
      if (dash.refreshIntervalMs !== undefined) meta.refreshIntervalMs = dash.refreshIntervalMs;
      if (Object.keys(meta).length) {
        writeJsonFile(path.join(outDir, "dashboards", `${stem}.meta.json`), meta);
      }
    }
  }

  if (Array.isArray(manifest.alertRules)) {
    for (const rule of manifest.alertRules) {
      const name = rule.name ?? "alert";
      writeJsonFile(path.join(outDir, "alerts", `${name}.json`), rule);
    }
  }

  if (Array.isArray(manifest.correlators)) {
    for (const c of manifest.correlators) {
      const name = c.name ?? "correlator";
      writeJsonFile(path.join(outDir, "correlators", `${name}.json`), c);
    }
  }

  if (Array.isArray(manifest.reports)) {
    for (const report of manifest.reports) {
      const id = report.reportId ?? "report";
      const { query, ...meta } = report;
      if (query) {
        writeTextFile(path.join(outDir, "reports", `${id}.sql`), query);
      }
      writeJsonFile(path.join(outDir, "reports", `${id}.json`), meta);
    }
  }

  if (Array.isArray(manifest.events) && manifest.events.length) {
    writeJsonFile(path.join(outDir, "events.json"), { events: manifest.events });
  }

  if (Array.isArray(manifest.workflows)) {
    for (const wf of manifest.workflows) {
      const stem = workflowStem(wf);
      if (wf.bpmnXml) {
        writeTextFile(path.join(outDir, "workflows", `${stem}.bpmn`), wf.bpmnXml);
      }
      const meta = {};
      if (wf.path) meta.path = wf.path;
      if (wf.title) meta.title = wf.title;
      if (wf.status) meta.status = wf.status;
      if (wf.operatorAppId) meta.operatorAppId = wf.operatorAppId;
      if (Object.keys(meta).length) {
        writeJsonFile(path.join(outDir, "workflows", `${stem}.meta.json`), meta);
      }
    }
  }

  if (Array.isArray(manifest.schedules) && manifest.schedules.length) {
    writeJsonFile(path.join(outDir, "schedules.json"), { schedules: manifest.schedules });
  }

  if (Array.isArray(manifest.tests) && manifest.tests.length) {
    writeJsonFile(path.join(outDir, "tests.json"), { tests: manifest.tests });
  }
}

function dashboardStem(dash) {
  if (dash.path) {
    const parts = dash.path.split(".");
    return parts[parts.length - 1] || "dashboard";
  }
  return (dash.title ?? "dashboard").replace(/\s+/g, "-").toLowerCase();
}

function workflowStem(wf) {
  if (wf.path) {
    const parts = wf.path.split(".");
    return parts[parts.length - 1] || "workflow";
  }
  return "workflow";
}

function externalizeFunctionSteps(fn, functionsDir, basename) {
  const clone = structuredClone(fn);
  const body = clone.source?.body;
  if (typeof body === "string") {
    try {
      const script = JSON.parse(body);
      walkSteps(script, (step) => {
        if (step && typeof step.sql === "string" && step.sql.length > 80) {
          step.sql = externalizeSqlField(step.sql, functionsDir, `${basename}-step`);
        }
      });
      clone.source.body = JSON.stringify(script);
    } catch {
      /* leave body as-is */
    }
  }
  return clone;
}

function walkSteps(node, visit) {
  if (!node || typeof node !== "object") {
    return;
  }
  if (Array.isArray(node.steps)) {
    for (const step of node.steps) {
      visit(step);
      walkSteps(step, visit);
    }
  }
}

export function unpackFromFile(bundlePath, outDir) {
  const manifest = readJsonFile(bundlePath);
  unpackBundle(manifest, outDir);
}
