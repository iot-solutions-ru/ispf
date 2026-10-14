import Ajv2020 from "ajv/dist/2020.js";
import { packDirectory } from "./pack.mjs";
import { applyStrictShipCanon } from "./shipCanon.mjs";
import { compactStringify, defaultSchemaPath, readJsonFile, resolveInput } from "./util.mjs";

export function validateLocal(manifest, schemaPath = defaultSchemaPath()) {
  const schema = readJsonFile(schemaPath);
  const ajv = new Ajv2020({ allErrors: true, strict: false });
  const validate = ajv.compile(schema);
  const ok = validate(manifest);
  return {
    status: ok ? "OK" : "ERROR",
    errors: ok ? [] : (validate.errors ?? []).map(formatAjvError),
  };
}

function formatAjvError(err) {
  const path = err.instancePath || "/";
  return `${path}: ${err.message ?? "invalid"}`;
}

export async function validateRemote(appId, manifest, { dryRun = false, strict = false } = {}) {
  const baseUrl = (process.env.ISPF_BASE_URL ?? "http://localhost:8080").replace(/\/$/, "");
  const token = process.env.ISPF_API_TOKEN ?? "";
  const headers = { "Content-Type": "application/json" };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  const params = new URLSearchParams();
  if (dryRun) params.set("dryRun", "true");
  if (strict) params.set("strict", "true");
  const qs = params.toString() ? `?${params}` : "";
  const response = await fetch(
    `${baseUrl}/api/v1/applications/${encodeURIComponent(appId)}/bundle/validate${qs}`,
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
    throw new Error(`validate failed (${response.status}): ${text}`);
  }
  if (!response.ok) {
    throw new Error(`validate failed (${response.status}): ${text}`);
  }
  return json;
}

export async function runValidate(inputPath, { appId, localOnly, schemaPath, strict }) {
  const resolved = resolveInput(inputPath);
  let manifest;
  if (resolved.mode === "dir") {
    manifest = packDirectory(resolved.path);
  } else {
    manifest = readJsonFile(resolved.path);
  }

  const results = { local: null, remote: null, manifest };

  results.local = validateLocal(manifest, schemaPath ?? defaultSchemaPath());
  if (strict && results.local.status === "OK") {
    results.local = applyStrictShipCanon(results.local, manifest);
  }
  if (results.local.status !== "OK") {
    return results;
  }

  if (appId && !localOnly) {
    results.remote = await validateRemote(appId, manifest, { dryRun: false, strict: !!strict });
  }

  return results;
}
