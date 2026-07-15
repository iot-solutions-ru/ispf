import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const siteDir = path.dirname(fileURLToPath(import.meta.url));

function run(script) {
  const res = spawnSync(process.execPath, [path.join(siteDir, script)], {
    stdio: "inherit",
    cwd: siteDir,
  });
  if (res.status !== 0) process.exit(res.status ?? 1);
}

run("build-mimic-diagram.mjs");
run("build-topology-svg.mjs");

const siteId = "m11";

const bundle = {
  version: "1.0.1",
  displayName: `ITM topology — ${siteId}`,
  tablePrefix: "",
  schemaName: "it_infra_monitoring",
  metadata: {
    packageId: `itm-plugin-topology-${siteId}`,
    siteId,
    dependsOnApp: "it-infra-monitoring",
    dependsOnPackages: [`itm-plugin-inventory-${siteId}`],
    assets: ["assets/main_topology.svg"],
    topologyBindings: "topology-meta.json",
  },
  objects: [
    {
      parentPath: "root.platform.mimics",
      name: `itm-${siteId}-dcn`,
      type: "MIMIC",
      displayName: `М11 DCN topology`,
    },
  ],
  operatorUi: {
    appId: `itm-plugin-topology-${siteId}`,
    title: `ITM topology — ${siteId}`,
    defaultDashboard: "root.platform.dashboards.itm-dcn",
    dashboards: [
      {
        path: "root.platform.dashboards.itm-dcn",
        title: "Network Topology Map",
      },
    ],
  },
};

fs.writeFileSync(path.join(siteDir, "bundle.json"), JSON.stringify(bundle, null, 2));
console.log("Wrote topology bundle (operatorUi -> itm-dcn dashboard in it-infra-monitoring)");
