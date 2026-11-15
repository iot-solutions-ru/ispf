import { resolveBaseUrl, writeCredentials } from "./session.mjs";

export async function login({ username, password, totp, fetchImpl = fetch } = {}) {
  const user = (username || process.env.ISPF_USERNAME || "").trim();
  const pass = password || process.env.ISPF_PASSWORD || "";
  if (!user || !pass) {
    throw new Error("login requires --username and --password");
  }
  const payload = { username: user, password: pass };
  if (totp) payload.totpCode = totp;
  const baseUrl = resolveBaseUrl();
  const response = await fetchImpl(`${baseUrl}/api/v1/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const text = await response.text();
  let body;
  try {
    body = JSON.parse(text);
  } catch {
    throw new Error(`login failed (${response.status}): ${text}`);
  }
  if (!response.ok || !body?.token) {
    const detail = body?.message || body?.error || text;
    throw new Error(`login failed (${response.status}): ${detail}`);
  }
  // Prefer the server-returned username so process.env.ISPF_USERNAME does not
  // taint CLI stdout (CodeQL js/clear-text-logging).
  const savedUsername = String(body.username || user || "");
  writeCredentials({
    baseUrl,
    username: savedUsername,
    token: body.token,
    expiresAt: body.expiresAt ?? null,
  });
  return {
    baseUrl,
    username: savedUsername,
    expiresAt: body.expiresAt ?? null,
  };
}
