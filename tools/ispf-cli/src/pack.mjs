import fs from "node:fs";
import path from "node:path";
import {
  compactStringify,
  inlineFileRefs,
  migrationIdFromFilename,
  readJsonFile,
  sortMaps,
  stableStringify,
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

export function packDirectory(dir) {
  const solutionPath = path.join(dir, "solution.json");
  if (!fs.existsSync(solutionPath)) {
    throw new Error(`Missing solution.json in ${dir}`);
  }
  const solution = readJsonFile(solutionPath);
  const manifest = {};

  for (const key of SOLUTION_ROOT_KEYS) {
    if (solution[key] !== undefined) {
      manifest[key] = solution[key];
    }
  }

  const sqlDir = path.join(dir, "sql");
  if (fs.existsSync(sqlDir)) {
    const migrations = fs
      .readdirSync(sqlDir)
      .filter((f) => f.toLowerCase().endsWith(".sql"))
      .sort()
      .map((file) => ({
        id: migrationIdFromFilename(file),
        sql: fs.readFileSync(path.join(sqlDir, file), "utf8").replace(/\r\n/g, "\n").replace(/\n$/, ""),
      }));
    if (migrations.length) {
      manifest.migrations = migrations;
    }
  }

  const functionsDir = path.join(dir, "functions");
  if (fs.existsSync(functionsDir)) {
    const functions = fs
      .readdirSync(functionsDir)
      .filter((f) => f.endsWith(".json"))
      .sort()
      .map((file) => inlineFileRefs(readJsonFile(path.join(functionsDir, file)), functionsDir));
    if (functions.length) {
      manifest.functions = functions;
    }
  }

  const blueprintsDir = path.join(dir, "blueprints");
  if (fs.existsSync(blueprintsDir)) {
    const blueprints = fs
      .readdirSync(blueprintsDir)
      .filter((f) => f.endsWith(".json"))
      .sort()
      .map((file) => readJsonFile(path.join(blueprintsDir, file)));
    if (blueprints.length) {
      manifest.blueprints = blueprints;
    }
  }

  const objectsPath = path.join(dir, "objects.json");
  if (fs.existsSync(objectsPath)) {
    const objectsDoc = readJsonFile(objectsPath);
    manifest.objects = Array.isArray(objectsDoc) ? objectsDoc : objectsDoc.objects ?? [];
  }

  const bindingsPath = path.join(dir, "bindings.json");
  if (fs.existsSync(bindingsPath)) {
    const doc = readJsonFile(bindingsPath);
    manifest.bindings = Array.isArray(doc) ? doc : doc.bindings ?? [];
  }

  const dashboardsDir = path.join(dir, "dashboards");
  if (fs.existsSync(dashboardsDir)) {
    manifest.dashboards = collectDashboards(dashboardsDir);
  }

  const alertsDir = path.join(dir, "alerts");
  if (fs.existsSync(alertsDir)) {
    const alertRules = fs
      .readdirSync(alertsDir)
      .filter((f) => f.endsWith(".json"))
      .sort()
      .map((file) => readJsonFile(path.join(alertsDir, file)));
    if (alertRules.length) {
      manifest.alertRules = alertRules;
    }
  }

  const correlatorsDir = path.join(dir, "correlators");
  if (fs.existsSync(correlatorsDir)) {
    const correlators = fs
      .readdirSync(correlatorsDir)
      .filter((f) => f.endsWith(".json"))
      .sort()
      .map((file) => readJsonFile(path.join(correlatorsDir, file)));
    if (correlators.length) {
      manifest.correlators = correlators;
    }
  }

  const reportsDir = path.join(dir, "reports");
  if (fs.existsSync(reportsDir)) {
    manifest.reports = collectReports(reportsDir);
  }

  const eventsPath = path.join(dir, "events.json");
  if (fs.existsSync(eventsPath)) {
    const doc = readJsonFile(eventsPath);
    manifest.events = Array.isArray(doc) ? doc : doc.events ?? [];
  }

  const workflowsDir = path.join(dir, "workflows");
  if (fs.existsSync(workflowsDir)) {
    manifest.workflows = collectWorkflows(workflowsDir);
  }

  const schedulesPath = path.join(dir, "schedules.json");
  if (fs.existsSync(schedulesPath)) {
    const doc = readJsonFile(schedulesPath);
    manifest.schedules = Array.isArray(doc) ? doc : doc.schedules ?? [];
  }

  const testsPath = path.join(dir, "tests.json");
  if (fs.existsSync(testsPath)) {
    const doc = readJsonFile(testsPath);
    manifest.tests = Array.isArray(doc) ? doc : doc.tests ?? [];
  }

  return sortMaps(manifest);
}

function collectDashboards(dashboardsDir) {
  const layoutFiles = fs
    .readdirSync(dashboardsDir)
    .filter((f) => f.endsWith(".layout.json"))
    .sort();
  return layoutFiles.map((layoutFile) => {
    const stem = layoutFile.replace(/\.layout\.json$/, "");
    const metaPath = path.join(dashboardsDir, `${stem}.meta.json`);
    const meta = fs.existsSync(metaPath) ? readJsonFile(metaPath) : {};
    const layout = readJsonFile(path.join(dashboardsDir, layoutFile));
    const entry = {
      path: meta.path ?? `root.platform.dashboards.${stem}`,
      title: meta.title,
      refreshIntervalMs: meta.refreshIntervalMs,
      layoutJson: compactStringify(layout),
    };
    if (entry.title === undefined) {
      delete entry.title;
    }
    if (entry.refreshIntervalMs === undefined) {
      delete entry.refreshIntervalMs;
    }
    return entry;
  });
}

function collectReports(reportsDir) {
  const sqlFiles = fs
    .readdirSync(reportsDir)
    .filter((f) => f.toLowerCase().endsWith(".sql"))
    .sort();
  const reports = sqlFiles.map((sqlFile) => {
    const stem = sqlFile.replace(/\.sql$/i, "");
    const metaPath = path.join(reportsDir, `${stem}.json`);
    const meta = fs.existsSync(metaPath) ? readJsonFile(metaPath) : { reportId: stem };
    const query = fs.readFileSync(path.join(reportsDir, sqlFile), "utf8").replace(/\r\n/g, "\n").replace(/\n$/, "");
    return { ...meta, query };
  });
  reports.sort((a, b) => String(a.reportId).localeCompare(String(b.reportId)));
  return reports;
}

function collectWorkflows(workflowsDir) {
  const bpmnFiles = fs
    .readdirSync(workflowsDir)
    .filter((f) => f.toLowerCase().endsWith(".bpmn"))
    .sort();
  return bpmnFiles.map((bpmnFile) => {
    const stem = bpmnFile.replace(/\.bpmn$/i, "");
    const metaPath = path.join(workflowsDir, `${stem}.meta.json`);
    const meta = fs.existsSync(metaPath) ? readJsonFile(metaPath) : {};
    const bpmnXml = fs.readFileSync(path.join(workflowsDir, bpmnFile), "utf8");
    return {
      path: meta.path ?? `root.platform.workflows.${stem}`,
      title: meta.title,
      status: meta.status,
      operatorAppId: meta.operatorAppId,
      bpmnXml,
    };
  });
}

export function packToFile(dir, outPath) {
  const manifest = packDirectory(dir);
  fs.mkdirSync(path.dirname(outPath), { recursive: true });
  fs.writeFileSync(outPath, `${stableStringify(manifest)}\n`, "utf8");
  return manifest;
}
