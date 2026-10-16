/**
 * Configure SQL binding root.platform.bindings.testBinding → variable on test1.
 * Usage: node examples/configure-testBinding.mjs
 * Env: ISPF_URL, ISPF_USER, ISPF_PASSWORD, TARGET_VARIABLE (optional)
 */
const baseUrl = process.env.ISPF_URL || "http://127.0.0.1:8080";
const username = process.env.ISPF_USER || "admin";
const password = process.env.ISPF_PASSWORD || "admin";
const TARGET_OBJECT = "root.platform.singleton-blueprints.test1";
const BINDING_PATH = "root.platform.bindings.testBinding";
/** Scalar variable on test1; created by SQL binding upsert if missing. */
const TARGET_VARIABLE = process.env.TARGET_VARIABLE?.trim() || "testBinding";

function pickVariable() {
  return TARGET_VARIABLE;
}

async function login() {
  const res = await fetch(`${baseUrl}/api/v1/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password, provider: "local" }),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`Login failed (${res.status}): ${text}`);
  const json = JSON.parse(text);
  const token = json.token ?? json.accessToken;
  if (!token) throw new Error("Login response has no token");
  return token;
}

async function api(token, method, path, body) {
  const url = path.startsWith("http") ? path : `${baseUrl}${path}`;
  const res = await fetch(url, {
    method,
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = text;
  }
  return { ok: res.ok, status: res.status, json, text };
}

const report = {};

try {
  const token = await login();
  const bindingPath = `/api/v1/sql-bindings/by-path?path=${encodeURIComponent(BINDING_PATH)}`;
  let bindingGet = await api(token, "GET", bindingPath);
  report.step2_getBinding = { status: bindingGet.status, body: bindingGet.json ?? bindingGet.text };

  if (bindingGet.status === 404) {
    const postRes = await api(token, "POST", "/api/v1/sql-bindings", { bindingId: "testBinding" });
    report.step2_createBinding = { status: postRes.status, body: postRes.json ?? postRes.text };
    if (!postRes.ok) throw new Error(`POST sql-bindings failed (${postRes.status}): ${postRes.text}`);
    bindingGet = await api(token, "GET", bindingPath);
    if (!bindingGet.ok) throw new Error(`GET binding after create failed (${bindingGet.status}): ${bindingGet.text}`);
  } else if (!bindingGet.ok) {
    throw new Error(`GET binding failed (${bindingGet.status}): ${bindingGet.text}`);
  }

  const existing = bindingGet.json;
  const editorPath = `/api/v1/objects/by-path/editor?path=${encodeURIComponent(TARGET_OBJECT)}`;
  const editor = await api(token, "GET", editorPath);
  report.step3_editor = {
    status: editor.status,
    variableNames: editor.ok ? editor.json?.variables?.map((v) => v.name) : editor.text,
  };
  if (!editor.ok) throw new Error(`Editor GET failed (${editor.status}): ${editor.text}`);

  const variable = pickVariable();
  report.selectedVariable = variable;

  const putBody = {
    bindingId: existing.bindingId ?? "testBinding",
    targetObjectPath: TARGET_OBJECT,
    variable,
    dataSourcePath: existing.dataSourcePath ?? "",
    query: existing.query ?? "",
    valueField: existing.valueField ?? "value",
    refresh: existing.refresh ?? "manual",
    refreshIntervalMs: existing.refreshIntervalMs ?? 30_000,
    triggerObjectPath: existing.triggerObjectPath ?? "",
    triggerFunctionName: existing.triggerFunctionName ?? "",
    enabled: existing.enabled ?? true,
  };

  const putRes = await api(token, "PUT", bindingPath, putBody);
  report.step4_putRequestBody = putBody;
  report.step4_putResponse = { status: putRes.status, body: putRes.json ?? putRes.text };
  if (!putRes.ok) throw new Error(`PUT failed (${putRes.status}): ${putRes.text}`);

  const finalGet = await api(token, "GET", bindingPath);
  report.step5_finalBinding = finalGet.ok ? finalGet.json : finalGet.text;
} catch (err) {
  report.error = String(err?.message ?? err);
  console.log(JSON.stringify(report, null, 2));
  process.exit(1);
}

console.log(JSON.stringify(report, null, 2));
