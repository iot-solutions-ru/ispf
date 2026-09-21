import { packDirectory } from "./pack.mjs";
import { compactStringify } from "./util.mjs";
import { validateLocal, validateRemote } from "./validate.mjs";
import { defaultSchemaPath } from "./util.mjs";

export async function deployDirectory(dir, appId, { schemaPath } = {}) {
  const manifest = packDirectory(dir);
  const local = validateLocal(manifest, schemaPath ?? defaultSchemaPath());
  if (local.status !== "OK") {
    const err = new Error("Local schema validation failed");
    err.details = local.errors;
    throw err;
  }

  const dryRun = await validateRemote(appId, manifest, { dryRun: true });
  if (dryRun.status !== "OK") {
    const err = new Error("Server dry-run validation failed");
    err.details = dryRun.errors;
    throw err;
  }

  const baseUrl = (process.env.ISPF_BASE_URL ?? "http://localhost:8080").replace(/\/$/, "");
  const token = process.env.ISPF_API_TOKEN ?? "";
  const headers = { "Content-Type": "application/json" };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(
    `${baseUrl}/api/v1/applications/${encodeURIComponent(appId)}/deploy`,
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
    throw new Error(`deploy failed (${response.status}): ${text}`);
  }
  return { dryRun, deploy: json };
}
