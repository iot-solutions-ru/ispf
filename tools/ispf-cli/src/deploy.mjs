import { packDirectory } from "./pack.mjs";
import { applyStrictShipCanon } from "./shipCanon.mjs";
import { compactStringify } from "./util.mjs";
import { validateLocal, validateRemote } from "./validate.mjs";
import { defaultSchemaPath } from "./util.mjs";

export async function deployDirectory(dir, appId, { schemaPath, strict = false } = {}) {
  const manifest = packDirectory(dir);
  let local = validateLocal(manifest, schemaPath ?? defaultSchemaPath());
  if (strict && local.status === "OK") {
    local = applyStrictShipCanon(local, manifest);
  }
  if (local.status !== "OK") {
    const err = new Error(strict ? "Local schema/ship-canon validation failed" : "Local schema validation failed");
    err.details = local.errors;
    throw err;
  }

  const dryRun = await validateRemote(appId, manifest, { dryRun: true, strict: !!strict });
  if (dryRun.status !== "OK") {
    const err = new Error(
      strict ? "Server dry-run ship-canon validation failed" : "Server dry-run validation failed"
    );
    err.details = dryRun.errors;
    throw err;
  }

  const baseUrl = (process.env.ISPF_BASE_URL ?? "http://localhost:8080").replace(/\/$/, "");
  const token = process.env.ISPF_API_TOKEN ?? "";
  const headers = { "Content-Type": "application/json" };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const qs = strict ? "?strict=true" : "";
  const response = await fetch(
    `${baseUrl}/api/v1/applications/${encodeURIComponent(appId)}/deploy${qs}`,
    {
      method: "POST",
      headers,
      body: compactStringify(manifest),
    }
  );
  const text = await response.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    throw new Error(`deploy failed (${response.status}): ${text}`);
  }
  if (!response.ok) {
    const err = new Error(`deploy failed (${response.status}): ${text}`);
    err.details = json;
    throw err;
  }
  return { dryRun, deploy: json };
}
