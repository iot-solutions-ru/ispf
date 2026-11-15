import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const DEFAULT_BASE_URL = "http://localhost:8080";

export function resolveBaseUrl() {
  const raw = (process.env.ISPF_BASE_URL ?? "").trim();
  return (raw || DEFAULT_BASE_URL).replace(/\/+$/, "");
}

export function credentialsPath() {
  const home = process.env.ISPF_HOME || os.homedir();
  return path.join(home, ".ispf", "credentials.json");
}

export function readCredentials() {
  try {
    return JSON.parse(fs.readFileSync(credentialsPath(), "utf8"));
  } catch {
    return null;
  }
}

export function writeCredentials(value) {
  const file = credentialsPath();
  const dir = path.dirname(file);
  fs.mkdirSync(dir, { recursive: true, mode: 0o700 });
  try {
    fs.chmodSync(dir, 0o700);
  } catch {
    // Windows does not apply the Unix mode.
  }
  const tmp = `${file}.${process.pid}.tmp`;
  fs.writeFileSync(tmp, `${JSON.stringify(value, null, 2)}\n`, { encoding: "utf8", mode: 0o600 });
  try {
    fs.chmodSync(tmp, 0o600);
  } catch {
    // Windows does not apply the Unix mode.
  }
  fs.renameSync(tmp, file);
}

function sameBaseUrl(value) {
  if (typeof value !== "string" || !value.trim()) return false;
  return value.trim().replace(/\/+$/, "") === resolveBaseUrl();
}

/** Env wins. A saved token is used only when it was issued for this base URL. */
export function resolveToken() {
  if (process.env.ISPF_API_TOKEN) return process.env.ISPF_API_TOKEN;
  const saved = readCredentials();
  if (!saved?.token || !sameBaseUrl(saved.baseUrl)) return "";
  return saved.token;
}

export function authHeaders(extra = {}) {
  const headers = { ...extra };
  const token = resolveToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  return headers;
}
