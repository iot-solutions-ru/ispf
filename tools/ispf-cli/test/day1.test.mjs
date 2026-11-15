import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { describe, test } from "node:test";
import { usageText, cliVersion } from "../src/cli.mjs";
import { doctor, formatDoctor } from "../src/doctor.mjs";
import { login } from "../src/login.mjs";
import { authHeaders, credentialsPath, resolveBaseUrl, resolveToken, writeCredentials } from "../src/session.mjs";

function useTempHome() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "ispf-cli-"));
  process.env.ISPF_HOME = dir;
  delete process.env.ISPF_API_TOKEN;
  process.env.ISPF_BASE_URL = "http://127.0.0.1:9";
  return dir;
}

function jsonResponse(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => JSON.stringify(body),
  };
}

describe("day-1 commands", { concurrency: 1 }, () => {
test("usage lists deploy once and prints the package version", () => {
  const deployLines = usageText().split("\n").filter((line) => line.includes("ispf deploy"));
  assert.equal(deployLines.length, 1);
  assert.match(deployLines[0], /--with-seeds/);
  assert.equal(cliVersion(), "0.1.0");
});

test("login stores the token and not the password", async () => {
  useTempHome();
  const fetchImpl = async (url, init) => {
    assert.match(url, /\/api\/v1\/auth\/login$/);
    const sent = JSON.parse(init.body);
    assert.equal(sent.username, "admin");
    assert.equal(sent.password, "admin");
    assert.equal(sent.totpCode, "123456");
    return jsonResponse(200, {
      token: "session-token",
      username: "admin",
      expiresAt: "2099-01-01T00:00:00Z",
    });
  };
  const saved = await login({
    username: "admin",
    password: "admin",
    totp: "123456",
    fetchImpl,
  });
  assert.equal(saved.username, "admin");
  const file = JSON.parse(fs.readFileSync(credentialsPath(), "utf8"));
  assert.equal(file.token, "session-token");
  assert.equal(file.baseUrl, "http://127.0.0.1:9");
  assert.equal(file.password, undefined);
  assert.equal(resolveToken(), "session-token");
});

test("a saved token is ignored when the base URL changes", async () => {
  useTempHome();
  await login({
    username: "admin",
    password: "admin",
    fetchImpl: async () => jsonResponse(200, { token: "for-this-host", username: "admin" }),
  });
  process.env.ISPF_BASE_URL = "http://other.example";
  assert.equal(resolveToken(), "");
  process.env.ISPF_API_TOKEN = "from-env";
  assert.equal(resolveToken(), "from-env");
});

test("doctor fails closed when the server is down or virtual is missing", async () => {
  useTempHome();
  const down = await doctor({
    fetchImpl: async () => {
      throw new Error("connect ECONNREFUSED");
    },
  });
  assert.equal(down.ok, false);
  assert.match(formatDoctor(down), /ECONNREFUSED/);

  const missing = await doctor({
    fetchImpl: async (url) => {
      if (url.endsWith("/api/v1/info")) {
        return jsonResponse(200, { shortName: "ISPF", version: "0.9.0", environment: "local" });
      }
      if (url.endsWith("/actuator/health")) return jsonResponse(200, { status: "UP" });
      if (url.endsWith("/api/v1/drivers")) return jsonResponse(200, [{ id: "mqtt" }]);
      throw new Error(url);
    },
  });
  assert.equal(missing.ok, false);
  assert.match(formatDoctor(missing), /virtual is missing/);
  assert.match(formatDoctor(missing), /no token/);
});

test("doctor rejects a token the server refuses", async () => {
  useTempHome();
  process.env.ISPF_API_TOKEN = "stale";
  const report = await doctor({
    fetchImpl: async (url) => {
      if (url.endsWith("/api/v1/info")) {
        return jsonResponse(200, { shortName: "ISPF", version: "0.9.0" });
      }
      if (url.endsWith("/actuator/health")) return jsonResponse(200, { status: "UP" });
      return jsonResponse(401, { error: "unauthorized" });
    },
  });
  assert.equal(report.ok, false);
  assert.match(formatDoctor(report), /token rejected/);
});

test("doctor passes when info, health, token, and virtual are present", async () => {
  useTempHome();
  process.env.ISPF_API_TOKEN = "from-env";
  const report = await doctor({
    fetchImpl: async (url, init) => {
      if (url.endsWith("/api/v1/drivers")) {
        assert.equal(init.headers.Authorization, "Bearer from-env");
        return jsonResponse(200, [{ id: "virtual" }, { id: "mqtt" }]);
      }
      if (url.endsWith("/api/v1/info")) {
        return jsonResponse(200, { shortName: "ISPF", version: "0.9.0", environment: "local" });
      }
      return jsonResponse(200, { status: "UP" });
    },
  });
  assert.equal(report.ok, true);
  assert.match(formatDoctor(report), /virtual present/);
  assert.equal(authHeaders()["Authorization"], "Bearer from-env");
});

test("an empty base URL falls back, and a token without a matching URL is ignored", () => {
  useTempHome();
  process.env.ISPF_BASE_URL = "";
  assert.equal(resolveBaseUrl(), "http://localhost:8080");
  writeCredentials({ token: "orphan" });
  assert.equal(resolveToken(), "");
  assert.equal(authHeaders().Authorization, undefined);
  process.env.ISPF_BASE_URL = "http://localhost:8080///";
  writeCredentials({ baseUrl: "http://localhost:8080/", token: "matched" });
  assert.equal(resolveToken(), "matched");
});

test("doctor fails when health is not UP", async () => {
  useTempHome();
  process.env.ISPF_API_TOKEN = "from-env";
  const report = await doctor({
    fetchImpl: async (url) => {
      if (url.endsWith("/api/v1/info")) return jsonResponse(200, { version: "0.9.0" });
      if (url.endsWith("/actuator/health")) return jsonResponse(200, { status: "DOWN" });
      return jsonResponse(200, [{ id: "virtual" }]);
    },
  });
  assert.equal(report.ok, false);
  assert.match(formatDoctor(report), /Health\s+FAIL\s+DOWN/);
});
});
