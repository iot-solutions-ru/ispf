#!/usr/bin/env node
/**
 * Fill hollow marketplace catalog stubs with Operator-ready dashboards.
 * Updates examples/* sources, then copies into examples/marketplace-catalog/*.
 */
import fs from "node:fs";
import path from "node:path";

const root = path.resolve(import.meta.dirname, "../..");

function readJson(p) {
  return JSON.parse(fs.readFileSync(p, "utf8"));
}

function writeJson(p, obj) {
  fs.writeFileSync(p, JSON.stringify(obj, null, 2) + "\n", "utf8");
}

function layout(widgets) {
  return JSON.stringify({ columns: 84, rowHeight: 8, widgets });
}

function fnWidget(id, title, x, y, w, h, objectPath, functionName, buttonLabel, inputMap) {
  const wgt = {
    id,
    type: "function",
    title,
    x,
    y,
    w,
    h,
    objectPath,
    functionName,
    buttonLabel,
  };
  if (inputMap) wgt.inputMapJson = JSON.stringify(inputMap);
  return wgt;
}

function htmlWidget(id, title, x, y, w, h, html) {
  return {
    id,
    type: "html-snippet",
    title,
    x,
    y,
    w,
    h,
    htmlJson: html,
  };
}

function valueWidget(id, title, x, y, w, h, objectPath, variableName, unit) {
  const wgt = {
    id,
    type: "value",
    title,
    x,
    y,
    w,
    h,
    objectPath,
    variableName,
    valueField: "value",
    decimals: 2,
  };
  if (unit) wgt.unit = unit;
  return wgt;
}

function setOperatorUi(bundle, appId, title, dashboards, eventJournalObjectPath) {
  const defaultDashboard = dashboards[0]?.path || "";
  bundle.operatorUi = {
    appId,
    title,
    defaultDashboard,
    dashboards: dashboards.map((d) => ({ path: d.path, title: d.navTitle || d.title })),
  };
  if (eventJournalObjectPath) {
    bundle.operatorUi.eventJournalObjectPath = eventJournalObjectPath;
  }
}

function bumpPatch(version) {
  const parts = String(version || "1.0.0").split(".").map((n) => Number(n) || 0);
  while (parts.length < 3) parts.push(0);
  parts[2] += 1;
  return parts.join(".");
}

function patchMesReference() {
  const p = path.join(root, "examples/mes-reference/bundle.json");
  const j = readJson(p);
  j.version = bumpPatch(j.version);
  j.metadata = {
    ...(j.metadata || {}),
    changelog: `${j.version} — Operator dispatch dashboard (list/start/complete filling)`,
  };
  const hub = "root.platform.devices.demo-sensor-01";
  j.dashboards = [
    {
      path: "root.platform.dashboards.mes-reference-dispatch",
      title: "MES Reference Dispatch",
      refreshIntervalMs: 5000,
      layoutJson: layout([
        htmlWidget(
          "help",
          "Dispatch",
          0,
          0,
          84,
          10,
          "<p>Dispatch orders from <code>mes_dispatch_order</code>. List → start filling (DO-1001) → complete.</p>"
        ),
        fnWidget("list", "Orders", 0, 10, 42, 28, hub, "mes_listOrders", "List orders"),
        fnWidget(
          "start",
          "Start filling",
          42,
          10,
          42,
          14,
          hub,
          "mes_startFilling",
          "Start DO-1001",
          { orderNo: "DO-1001" }
        ),
        fnWidget(
          "complete",
          "Complete filling",
          42,
          24,
          42,
          14,
          hub,
          "mes_completeFilling",
          "Complete DO-1001",
          { orderNo: "DO-1001" }
        ),
      ]),
    },
  ];
  setOperatorUi(
    j,
    "mes-reference",
    "MES Reference",
    [{ path: j.dashboards[0].path, title: "Dispatch", navTitle: "Dispatch" }],
    hub
  );
  writeJson(p, j);
  return p;
}

function patchMesOeeReference() {
  const p = path.join(root, "examples/mes-oee-reference/bundle.json");
  const j = readJson(p);
  j.version = bumpPatch(j.version);
  j.metadata = {
    ...(j.metadata || {}),
    changelog: `${j.version} — Operator OEE dashboard (shifts / KPI / downtime)`,
  };
  const hub = "root.platform.devices.demo-sensor-01";
  const shiftId = "dddddddd-dddd-dddd-dddd-dddddddddddd";
  j.dashboards = [
    {
      path: "root.platform.dashboards.mes-oee-reference",
      title: "MES OEE Reference",
      refreshIntervalMs: 5000,
      layoutJson: layout([
        fnWidget("shifts", "Shifts", 0, 0, 28, 21, hub, "mes_oee_listShifts", "List shifts"),
        fnWidget("kpi", "OEE KPI", 28, 0, 56, 21, hub, "mes_oee_getKpi", "Morning shift KPI", {
          shiftId,
        }),
        fnWidget(
          "downtime",
          "Add downtime",
          0,
          21,
          42,
          21,
          hub,
          "mes_oee_addDowntime",
          "Add 5 min",
          { shiftId, minutes: 5 }
        ),
        htmlWidget(
          "help",
          "Hint",
          42,
          21,
          42,
          21,
          "<p>Seed shift <code>LINE-A01 / Morning</code>. Use Add downtime then reload KPI.</p>"
        ),
      ]),
    },
  ];
  setOperatorUi(
    j,
    "mes-oee-reference",
    "MES OEE Reference",
    [{ path: j.dashboards[0].path, title: "OEE", navTitle: "OEE" }],
    hub
  );
  writeJson(p, j);
  return p;
}

function patchWarehouse() {
  const p = path.join(root, "examples/warehouse-app/bundle.json");
  const j = readJson(p);
  j.version = bumpPatch(j.version);
  j.metadata = {
    ...(j.metadata || {}),
    changelog: `${j.version} — Operator warehouse locations dashboard`,
  };
  const hub = "root.platform.devices.demo-sensor-01";
  j.dashboards = [
    {
      path: "root.platform.dashboards.warehouse-locations",
      title: "Warehouse Locations",
      refreshIntervalMs: 10000,
      layoutJson: layout([
        htmlWidget(
          "help",
          "Warehouse",
          0,
          0,
          84,
          10,
          "<p>Locations from <code>wh_location</code> via <code>warehouse_listLocations</code>.</p>"
        ),
        fnWidget(
          "locations",
          "Locations",
          0,
          10,
          84,
          32,
          hub,
          "warehouse_listLocations",
          "List locations"
        ),
      ]),
    },
  ];
  setOperatorUi(
    j,
    "warehouse",
    "Warehouse",
    [{ path: j.dashboards[0].path, title: "Locations", navTitle: "Locations" }],
    hub
  );
  writeJson(p, j);
  return p;
}

function patchMarketplaceDemo() {
  const p = path.join(root, "examples/marketplace-demo/bundle.json");
  const j = readJson(p);
  j.version = bumpPatch(j.version);
  j.metadata = {
    ...(j.metadata || {}),
    changelog: `${j.version} — Operator install-smoke dashboard + notes report`,
    domain: "marketplace-demo",
  };
  j.reports = [
    {
      reportId: "install-notes",
      title: "Install notes",
      description: "Rows from demo_note after marketplace install",
      query: "SELECT message FROM demo_note ORDER BY message",
      parameters: [],
      columns: [{ field: "message", label: "Message" }],
      maxRows: 100,
    },
  ];
  j.dashboards = [
    {
      path: "root.platform.dashboards.marketplace-demo-home",
      title: "Marketplace Demo",
      refreshIntervalMs: 15000,
      layoutJson: layout([
        htmlWidget(
          "welcome",
          "Installed",
          0,
          0,
          84,
          14,
          "<p>Marketplace demo bundle installed successfully. Open the install-notes report to verify seed data.</p>"
        ),
        {
          id: "notes-report",
          type: "report",
          title: "Install notes",
          x: 0,
          y: 14,
          w: 84,
          h: 28,
          reportPath: "root.platform.reports.install-notes",
        },
      ]),
    },
  ];
  setOperatorUi(j, "marketplace-demo", "Marketplace Demo", [
    { path: j.dashboards[0].path, title: "Home", navTitle: "Home" },
  ]);
  j.operatorUi.reports = [
    { path: "root.platform.reports.install-notes", title: "Install notes" },
  ];
  j.operatorUi.defaultReport = "root.platform.reports.install-notes";
  j.operatorUi.eventJournalObjectPath = "root.platform";
  writeJson(p, j);
  return p;
}

function patchSimulator() {
  const p = path.join(root, "examples/simulator-profiles/bundle.json");
  const j = readJson(p);
  j.version = bumpPatch(j.version);
  j.metadata = {
    ...(j.metadata || {}),
    changelog: `${j.version} — Operator overview for virtual meter / weighbridge`,
  };
  j.dashboards = [
    {
      path: "root.platform.dashboards.simulator-overview",
      title: "Simulator Overview",
      refreshIntervalMs: 5000,
      layoutJson: layout([
        htmlWidget(
          "help",
          "Profiles",
          0,
          0,
          84,
          12,
          "<p>PF-09 virtual devices: <code>sim-meter-01</code> and <code>sim-weighbridge-01</code>. Bindings come from the virtual driver after connect.</p>"
        ),
        valueWidget(
          "meter",
          "Meter liters",
          0,
          12,
          42,
          18,
          "root.platform.devices.sim-meter-01",
          "meterLiters",
          "L"
        ),
        valueWidget(
          "weigh",
          "Weighbridge liters",
          42,
          12,
          42,
          18,
          "root.platform.devices.sim-weighbridge-01",
          "meterLiters",
          "L"
        ),
      ]),
    },
  ];
  setOperatorUi(j, "simulator", "Simulator Profiles", [
    { path: j.dashboards[0].path, title: "Overview", navTitle: "Overview" },
  ]);
  writeJson(p, j);
  return p;
}

function copyMesPlatform() {
  const src = path.join(root, "examples/mes-platform/bundle.json");
  const j = readJson(src);
  // Keep source as-is; catalog gets a copy. Bump catalog listing separately.
  return src;
}

const catalogCopies = [
  { src: "examples/mes-platform/bundle.json", dest: "examples/marketplace-catalog/mes-platform/bundle.json", listing: "mes-platform", bumpListingTo: "1.1.1", changelog: "1.1.1 — full Operator UI (Dispatch / OEE / Quality) from mes-platform product bundle" },
  { src: "examples/mes-reference/bundle.json", dest: "examples/marketplace-catalog/mes-reference/bundle.json", listing: "mes-reference", bumpListingTo: null, changelog: null },
  { src: "examples/mes-oee-reference/bundle.json", dest: "examples/marketplace-catalog/mes-oee-reference/bundle.json", listing: "mes-oee-reference", bumpListingTo: null, changelog: null },
  { src: "examples/warehouse-app/bundle.json", dest: "examples/marketplace-catalog/warehouse-reference/bundle.json", listing: "warehouse-reference", bumpListingTo: null, changelog: null },
  { src: "examples/marketplace-demo/bundle.json", dest: "examples/marketplace-catalog/marketplace-demo/bundle.json", listing: "marketplace-demo", bumpListingTo: null, changelog: null },
  { src: "examples/simulator-profiles/bundle.json", dest: "examples/marketplace-catalog/simulator-profiles/bundle.json", listing: "simulator-profiles", bumpListingTo: null, changelog: null },
];

function syncListing(slug, bundleVersion, changelogOverride) {
  const lp = path.join(root, `examples/marketplace-catalog/${slug}/listing.manifest.json`);
  const listing = readJson(lp);
  listing.latestVersion = bundleVersion;
  if (changelogOverride) listing.changelog = changelogOverride;
  else if (listing.changelog) {
    listing.changelog = `${bundleVersion} — Operator-ready dashboards (install opens UI in Operator)`;
  }
  writeJson(lp, listing);
}

const patched = [
  patchMesReference(),
  patchMesOeeReference(),
  patchWarehouse(),
  patchMarketplaceDemo(),
  patchSimulator(),
  copyMesPlatform(),
];

for (const item of catalogCopies) {
  const srcAbs = path.join(root, item.src);
  const destAbs = path.join(root, item.dest);
  fs.copyFileSync(srcAbs, destAbs);
  const bundle = readJson(destAbs);
  // For mes-platform keep 1.1.0 content but bump listing to 1.1.1 and optionally bump bundle version for publish uniqueness
  if (item.listing === "mes-platform") {
    bundle.version = item.bumpListingTo;
    bundle.metadata = {
      ...(bundle.metadata || {}),
      changelog: item.changelog,
    };
    writeJson(destAbs, bundle);
    // also bump source examples/mes-platform for consistency
    const srcBundle = readJson(srcAbs);
    srcBundle.version = item.bumpListingTo;
    srcBundle.metadata = {
      ...(srcBundle.metadata || {}),
      changelog: item.changelog,
    };
    writeJson(srcAbs, srcBundle);
    fs.copyFileSync(srcAbs, destAbs);
    syncListing(item.listing, item.bumpListingTo, item.changelog);
  } else {
    syncListing(item.listing, bundle.version, item.changelog);
  }
  console.log(`OK ${item.listing} → v${readJson(destAbs).version} opDash=${(readJson(destAbs).operatorUi?.dashboards || []).length}`);
}

console.log("Patched sources:", patched.length);
