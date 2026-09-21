import { packDirectory } from "./pack.mjs";
import { compactStringify, sortMaps } from "./util.mjs";

export async function diffDirectory(dir, appId) {
  const local = sortMaps(packDirectory(dir));
  const remote = await fetchRemoteManifest(appId);
  const summary = diffSummary(local, remote);
  return { local, remote, summary };
}

async function fetchRemoteManifest(appId) {
  const baseUrl = (process.env.ISPF_BASE_URL ?? "http://localhost:8080").replace(/\/$/, "");
  const token = process.env.ISPF_API_TOKEN ?? "";
  const headers = {};
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  const response = await fetch(
    `${baseUrl}/api/v1/applications/${encodeURIComponent(appId)}/export?canonical=true`,
    { headers }
  );
  const text = await response.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    throw new Error(`export failed (${response.status}): ${text}`);
  }
  if (!response.ok) {
    throw new Error(`export failed (${response.status}): ${text}`);
  }
  return sortMaps(json.manifest ?? json);
}

/** Shallow path summary for top-level and nested array length changes. */
export function diffSummary(local, remote) {
  const changes = [];
  const keys = new Set([...Object.keys(local), ...Object.keys(remote)]);
  for (const key of [...keys].sort()) {
    const a = local[key];
    const b = remote[key];
    if (JSON.stringify(a) === JSON.stringify(b)) {
      continue;
    }
    if (a === undefined) {
      changes.push({ op: "add", path: `/${key}`, note: "only on server" });
    } else if (b === undefined) {
      changes.push({ op: "remove", path: `/${key}`, note: "only in repo" });
    } else if (Array.isArray(a) && Array.isArray(b) && a.length !== b.length) {
      changes.push({
        op: "replace",
        path: `/${key}`,
        note: `array length ${a.length} (repo) vs ${b.length} (server)`,
      });
    } else {
      changes.push({ op: "replace", path: `/${key}`, note: "content differs" });
    }
  }
  return changes;
}

export function printDiff(summary, local, remote) {
  if (summary.length === 0) {
    console.log("No differences (canonical JSON match).");
    return;
  }
  console.log("Diff summary:");
  for (const item of summary) {
    console.log(`  ${item.op} ${item.path} — ${item.note}`);
  }
  console.log("\nLocal canonical size:", compactStringify(local).length);
  console.log("Remote canonical size:", compactStringify(remote).length);
}
