import { readCredentials, resolveBaseUrl, resolveToken } from "./session.mjs";

async function getJson(url, token, fetchImpl) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  const response = await fetchImpl(url, { headers });
  const text = await response.text();
  let body = null;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    body = text;
  }
  return { status: response.status, ok: response.ok, body };
}

function line(ok, name, detail) {
  return { ok, name, detail };
}

export async function doctor({ fetchImpl = fetch } = {}) {
  const baseUrl = resolveBaseUrl();
  const token = resolveToken();
  const saved = readCredentials();
  const lines = [];

  let info;
  try {
    info = await getJson(`${baseUrl}/api/v1/info`, "", fetchImpl);
  } catch (err) {
    lines.push(line(false, "Server", `${baseUrl} — ${err.message ?? err}`));
    lines.push(line(false, "Health", "not checked"));
    lines.push(line(false, "Auth", token ? "not checked" : "no token"));
    lines.push(line(false, "Drivers", "not checked"));
    return { ok: false, baseUrl, lines };
  }
  if (!info.ok || !info.body?.version) {
    lines.push(line(false, "Server", `${baseUrl} — HTTP ${info.status}`));
  } else {
    const env = info.body.environment ? ` (${info.body.environment})` : "";
    lines.push(line(true, "Server", `${info.body.shortName ?? "ISPF"} ${info.body.version}${env}`));
  }

  try {
    const health = await getJson(`${baseUrl}/actuator/health`, "", fetchImpl);
    const status = health.body?.status;
    lines.push(
      status === "UP"
        ? line(true, "Health", "UP")
        : line(false, "Health", status ? String(status) : `HTTP ${health.status}`)
    );
  } catch (err) {
    lines.push(line(false, "Health", err.message ?? String(err)));
  }

  if (!token) {
    lines.push(line(false, "Auth", "no token. Run: ispf login --username admin --password admin"));
  } else if (process.env.ISPF_API_TOKEN) {
    lines.push(line(true, "Auth", "ISPF_API_TOKEN is set"));
  } else if (saved?.username) {
    lines.push(line(true, "Auth", `saved token for ${saved.username}`));
  } else {
    lines.push(line(true, "Auth", "saved token"));
  }

  try {
    const drivers = await getJson(`${baseUrl}/api/v1/drivers`, token, fetchImpl);
    if (drivers.status === 401 || drivers.status === 403) {
      lines.push(line(false, "Drivers", `HTTP ${drivers.status}`));
      if (token) {
        const auth = lines.find((item) => item.name === "Auth");
        if (auth) {
          auth.ok = false;
          auth.detail = `token rejected (HTTP ${drivers.status})`;
        }
      }
    } else if (!drivers.ok || !Array.isArray(drivers.body)) {
      lines.push(line(false, "Drivers", `HTTP ${drivers.status}`));
      const auth = lines.find((item) => item.name === "Auth");
      if (auth?.ok) {
        auth.ok = false;
        auth.detail = "token not verified";
      }
    } else {
      const ids = drivers.body.map((item) => item?.id).filter(Boolean);
      const virtual = ids.includes("virtual");
      lines.push(
        virtual
          ? line(true, "Drivers", `${ids.length} registered, virtual present`)
          : line(false, "Drivers", `${ids.length} registered, virtual is missing`)
      );
    }
  } catch (err) {
    lines.push(line(false, "Drivers", err.message ?? String(err)));
  }

  return { ok: lines.every((item) => item.ok), baseUrl, lines };
}

export function formatDoctor(report) {
  const rows = [`ISPF doctor`, `  URL:     ${report.baseUrl}`];
  for (const item of report.lines) {
    const mark = item.ok ? "OK  " : "FAIL";
    rows.push(`  ${item.name.padEnd(8, " ")} ${mark}  ${item.detail}`);
  }
  return rows.join("\n");
}
