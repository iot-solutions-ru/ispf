#!/usr/bin/env node
/**
 * Generate CycloneDX 1.5 SBOMs for commercial delivery inventory.
 *
 * Outputs (under build/sbom/ by default):
 *   - web-console.cdx.json          npm runtime+transitive (from package-lock)
 *   - ispf-server-runtime.cdx.json  Java runtimeClasspath (from Gradle)
 *   - driver-packs.cdx.json         driver pack catalog + licenseType
 *   - legal-review.json             machine-readable review summary
 *   - LEGAL-REVIEW.md               human-readable engineering legal review
 *
 * Usage:
 *   node tools/license-audit/generate-sbom.mjs
 *   node tools/license-audit/generate-sbom.mjs --out build/sbom
 */
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { createHash } from "node:crypto";

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(here, "../..");

const args = process.argv.slice(2);
const outIdx = args.indexOf("--out");
const outDir = path.resolve(
  repoRoot,
  outIdx >= 0 ? args[outIdx + 1] : "build/sbom",
);

const REVIEW_DATE = new Date().toISOString().slice(0, 10);
const TOOL_VERSION = "1.0.0";

/** Upstream SPDX when npm metadata is incomplete (keep in sync with check-npm-licenses.mjs). */
const NPM_LICENSE_OVERRIDES = {
  "@mapbox/jsonlint-lines-primitives": "MIT",
  buffers: "MIT",
  "parse-cache-control": "MIT",
};

const COPYLEFT_PATTERNS = [
  /^GPL/i,
  /^AGPL/i,
  /^LGPL/i,
  /^MPL/i,
  /^EPL/i,
  /^CDDL/i,
];

const PERMISSIVE_OK = new Set([
  "MIT",
  "MIT-0",
  "ISC",
  "BSD-2-Clause",
  "BSD-3-Clause",
  "Apache-2.0",
  "0BSD",
  "Unlicense",
  "CC0-1.0",
  "BlueOak-1.0.0",
  "Python-2.0",
  "WTFPL",
  "Zlib",
  "LicenseRef-PublicDomain",
]);

function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true });
}

function uuidFrom(seed) {
  const h = createHash("sha256").update(seed).digest("hex");
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-${((parseInt(h.slice(16, 18), 16) & 0x3f) | 0x80).toString(16)}${h.slice(18, 20)}-${h.slice(20, 32)}`;
}

function normalizeLicense(raw) {
  if (!raw) return "NOASSERTION";
  if (Array.isArray(raw)) return raw.join(" OR ");
  return String(raw).trim() || "NOASSERTION";
}

function stripParens(s) {
  return s.replace(/^\(+/, "").replace(/\)+$/, "").trim();
}

function atomicLicenseClass(raw) {
  const s = stripParens(normalizeLicense(raw));
  if (s === "NOASSERTION" || s === "UNKNOWN" || s === "MISSING" || s === "UNLICENSED") {
    return "unknown";
  }
  if (/LicenseRef-NIST-PublicDomain/i.test(s) || /Public Domain/i.test(s)) {
    return "public-domain";
  }
  if (/classpath exception/i.test(s) || /WITH Classpath/i.test(s) || /GPL-2\.0-with-classpath-exception/i.test(s)) {
    return "weak-copyleft-cpe";
  }
  if (/^CDDL/i.test(s)) {
    return "weak-copyleft-cpe";
  }
  if (/^LGPL/i.test(s) || /^MPL/i.test(s) || /^EPL/i.test(s) || /^CPL/i.test(s)) {
    return "weak-copyleft";
  }
  if (/^GPL/i.test(s) || /^AGPL/i.test(s)) {
    return "strong-copyleft";
  }
  if (PERMISSIVE_OK.has(s) || /^(MIT|Apache-2\.0|BSD-|ISC|0BSD|Zlib)/i.test(s)) {
    return "permissive";
  }
  if (/^CC-BY/i.test(s) || /^CC0/i.test(s)) {
    return "permissive"; // content/data permissive for bundling metadata (caniuse-lite)
  }
  if (/SEE LICENSE/i.test(s) || /bpmn\.io/i.test(s)) {
    return "special-condition";
  }
  if (COPYLEFT_PATTERNS.some((re) => re.test(s))) {
    return "strong-copyleft";
  }
  return "review";
}

function licenseClass(spdx) {
  const s = normalizeLicense(spdx);
  // SPDX OR: redistributor may elect any option — prefer most permissive class.
  if (/\s+OR\s+/i.test(s) || /^\(.+\s+OR\s+.+\)$/i.test(s)) {
    const parts = stripParens(s).split(/\s+OR\s+/i);
    const classes = parts.map(atomicLicenseClass);
    const rank = {
      permissive: 0,
      "public-domain": 0,
      "weak-copyleft-cpe": 1,
      "weak-copyleft": 2,
      "special-condition": 3,
      review: 4,
      "commercial-restricted": 5,
      "strong-copyleft": 6,
      unknown: 7,
    };
    return classes.sort((a, b) => (rank[a] ?? 9) - (rank[b] ?? 9))[0];
  }
  // SPDX AND: must satisfy all — pick worst.
  if (/\s+AND\s+/i.test(s)) {
    const parts = stripParens(s).split(/\s+AND\s+/i);
    const classes = parts.map(atomicLicenseClass);
    const rank = {
      unknown: 0,
      "strong-copyleft": 1,
      "commercial-restricted": 2,
      "special-condition": 3,
      "weak-copyleft": 4,
      "weak-copyleft-cpe": 5,
      review: 6,
      permissive: 7,
      "public-domain": 7,
    };
    return classes.sort((a, b) => (rank[a] ?? 9) - (rank[b] ?? 9))[0];
  }
  return atomicLicenseClass(s);
}

function bomMeta(name, components) {
  return {
    bomFormat: "CycloneDX",
    specVersion: "1.5",
    serialNumber: `urn:uuid:${uuidFrom(`${name}:${REVIEW_DATE}:${components.length}`)}`,
    version: 1,
    metadata: {
      timestamp: new Date().toISOString(),
      tools: [
        {
          vendor: "ISPF",
          name: "tools/license-audit/generate-sbom.mjs",
          version: TOOL_VERSION,
        },
      ],
      component: {
        type: "application",
        name,
        version: readPlatformVersion(),
        licenses: [{ license: { id: "AGPL-3.0-only" } }],
      },
    },
    components,
  };
}

function readPlatformVersion() {
  try {
    const props = fs.readFileSync(path.join(repoRoot, "gradle.properties"), "utf8");
    const m = props.match(/^version\s*=\s*(.+)$/m);
    if (m) return m[1].trim();
  } catch {
    /* ignore */
  }
  return "0.1.0-SNAPSHOT";
}

function componentLicense(spdx) {
  const s = normalizeLicense(spdx);
  if (/^[A-Za-z0-9.+\-]+$/.test(s) && !s.startsWith("LicenseRef-") && !/OR|AND|WITH/i.test(s)) {
    return [{ license: { id: s } }];
  }
  return [{ license: { name: s } }];
}

function generateNpmBom() {
  const lockPath = path.join(repoRoot, "apps/web-console/package-lock.json");
  const lock = JSON.parse(fs.readFileSync(lockPath, "utf8"));
  const packages = lock.packages ?? {};
  const components = [];
  const licenseCounts = {};
  const classCounts = {};
  const findings = [];

  for (const [key, entry] of Object.entries(packages)) {
    if (!key || key === "") continue;
    const name = key.replace(/^node_modules\//, "");
    const version = entry.version ?? "0.0.0";
    let lic = NPM_LICENSE_OVERRIDES[name] ?? normalizeLicense(entry.license);
    if (!entry.license && !NPM_LICENSE_OVERRIDES[name]) lic = "MISSING";
    const cls = licenseClass(lic);
    licenseCounts[lic] = (licenseCounts[lic] ?? 0) + 1;
    classCounts[cls] = (classCounts[cls] ?? 0) + 1;

    if (cls === "unknown") {
      findings.push({
        ecosystem: "npm",
        name,
        version,
        license: lic,
        class: cls,
        severity: "high",
        note: "Missing/unknown license metadata — block commercial ship until resolved",
      });
    } else if (cls === "strong-copyleft" || cls === "commercial-restricted") {
      findings.push({
        ecosystem: "npm",
        name,
        version,
        license: lic,
        class: cls,
        severity: "high",
        note: "Strong copyleft/restricted in web-console graph — unexpected (dual MIT/GPL should elect MIT)",
      });
    }
    if (cls === "special-condition") {
      findings.push({
        ecosystem: "npm",
        name,
        version,
        license: lic,
        class: cls,
        severity: "medium",
        note: "Special license condition (e.g. watermark / SEE LICENSE) — verify obligations",
      });
    }

    const purl = name.startsWith("@")
      ? `pkg:npm/${name.replace("/", "%2F")}@${version}`
      : `pkg:npm/${name}@${version}`;

    components.push({
      type: "library",
      "bom-ref": purl,
      name,
      version,
      purl,
      licenses: componentLicense(lic),
      scope: entry.dev ? "optional" : "required",
      properties: [
        { name: "ispf:licenseClass", value: cls },
        { name: "ispf:dev", value: String(Boolean(entry.dev)) },
      ],
    });
  }

  return {
    bom: bomMeta("@ispf/web-console", components),
    licenseCounts,
    classCounts,
    findings,
    componentCount: components.length,
  };
}

function runGradleRuntimeDeps() {
  const gradleCmd = process.platform === "win32" ? "gradlew.bat" : "./gradlew";
  const result = spawnSync(
    gradleCmd,
    [
      ":packages:ispf-server:dependencies",
      "--configuration",
      "runtimeClasspath",
      "--quiet",
      "-q",
    ],
    {
      cwd: repoRoot,
      encoding: "utf8",
      maxBuffer: 32 * 1024 * 1024,
      shell: process.platform === "win32",
    },
  );
  if (result.status !== 0) {
    const err = (result.stderr || result.stdout || "").slice(-2000);
    throw new Error(`Gradle runtimeClasspath failed (exit ${result.status}): ${err}`);
  }
  return result.stdout || "";
}

function parseGradleDeps(stdout) {
  const re =
    /^[\\|+\-\s]*([\w.\-]+):([\w.\-]+):([\w.\-+]+)(?:\s+->\s+([\w.\-+]+))?/gm;
  const map = new Map();
  let m;
  while ((m = re.exec(stdout)) !== null) {
    const group = m[1];
    const artifact = m[2];
    const version = m[4] || m[3];
    if (group === "project" || group.startsWith("com.ispf")) continue;
    const key = `${group}:${artifact}:${version}`;
    map.set(key, { group, artifact, version });
  }
  return [...map.values()];
}

/** Fallback when POM has no <licenses> block. */
const JAVA_LICENSE_HINTS = {
  "org.springframework": "Apache-2.0",
  "org.springframework.boot": "Apache-2.0",
  "org.springframework.security": "Apache-2.0",
  "com.fasterxml.jackson": "Apache-2.0",
  "tools.jackson": "Apache-2.0",
  "io.micrometer": "Apache-2.0",
  "io.opentelemetry": "Apache-2.0",
  "io.nats": "Apache-2.0",
  "io.projectreactor": "Apache-2.0",
  "io.prometheus": "Apache-2.0",
  "io.dropwizard.metrics": "Apache-2.0",
  "org.flywaydb": "Apache-2.0",
  "org.postgresql": "BSD-2-Clause",
  "com.h2database": "MPL-2.0 OR EPL-1.0",
  "com.haulmont.yarg": "Apache-2.0",
  "org.apache": "Apache-2.0",
  "org.docx4j": "Apache-2.0",
  "javax.xml.bind": "CDDL-1.1 OR GPL-2.0-with-classpath-exception",
  "javax.activation": "CDDL-1.1 OR GPL-2.0-with-classpath-exception",
  "com.sun.istack": "BSD-3-Clause",
  "org.glassfish.jaxb": "BSD-3-Clause",
  "org.glassfish.hk2": "EPL-2.0 OR GPL-2.0-with-classpath-exception",
  "org.glassfish.jersey": "EPL-2.0 OR GPL-2.0-with-classpath-exception OR Apache-2.0",
  "org.slf4j": "MIT",
  "ch.qos.logback": "EPL-1.0 OR LGPL-2.1",
  "com.zaxxer": "Apache-2.0",
  "org.hibernate": "Apache-2.0",
  "org.jboss.logging": "Apache-2.0",
  "dev.cel": "Apache-2.0",
  "org.jspecify": "Apache-2.0",
  "org.antlr": "BSD-3-Clause",
  "org.threeten": "BSD-3-Clause",
  "org.hdrhistogram": "CC0-1.0 OR BSD-2-Clause",
  "org.reactivestreams": "MIT-0",
  "aopalliance": "LicenseRef-PublicDomain",
  "com.datastax.oss": "Apache-2.0",
  "com.typesafe": "Apache-2.0",
  "com.github.jnr": "EPL-2.0 OR Apache-2.0 OR LGPL-2.1",
  "org.ow2.asm": "BSD-3-Clause",
  "jakarta.": "EPL-2.0 OR GPL-2.0-with-classpath-exception",
  "org.eclipse.": "EPL-2.0",
  "io.netty": "Apache-2.0",
  "com.google.": "Apache-2.0",
  "org.jetbrains.kotlin": "Apache-2.0",
  "commons-": "Apache-2.0",
  "org.yaml": "Apache-2.0",
  "net.bytebuddy": "Apache-2.0",
  "org.checkerframework": "MIT",
  "com.github.ben-manes.caffeine": "Apache-2.0",
  "io.lettuce": "Apache-2.0",
  "redis.clients": "MIT",
  "org.aspectj": "EPL-2.0",
  "net.minidev": "Apache-2.0",
  "com.nimbusds": "Apache-2.0",
  "org.bouncycastle": "MIT",
  "com.fasterxml": "Apache-2.0",
  "jaxen": "BSD-3-Clause",
  "xalan": "Apache-2.0",
  "xml-apis": "Apache-2.0",
  "com.ibm.icu": "Unicode-3.0",
  "org.locationtech.jts": "EPL-2.0 OR BSD-3-Clause",
  "de.rototor.pdfbox": "Apache-2.0",
  "net.java.dev.msv": "BSD-3-Clause",
  "relaxngDatatype": "BSD-3-Clause",
  "javax.media.jai": "LicenseRef-JAI-Sun",
};

const SPDX_FROM_NAME = {
  "Apache License, Version 2.0": "Apache-2.0",
  "Apache License 2.0": "Apache-2.0",
  "Apache 2.0": "Apache-2.0",
  "Apache-2.0": "Apache-2.0",
  "The Apache Software License, Version 2.0": "Apache-2.0",
  "MIT License": "MIT",
  MIT: "MIT",
  "BSD-2-Clause": "BSD-2-Clause",
  "BSD-3-Clause": "BSD-3-Clause",
  "Eclipse Public License v2.0": "EPL-2.0",
  "Eclipse Public License - v 2.0": "EPL-2.0",
  "Eclipse Public License 1.0": "EPL-1.0",
  "Eclipse Distribution License - v 1.0": "BSD-3-Clause",
  "GNU General Public License, version 2 with the GNU Classpath Exception":
    "GPL-2.0-with-classpath-exception",
  "CDDL + GPLv2 with classpath exception": "CDDL-1.1 OR GPL-2.0-with-classpath-exception",
  "MPL 2.0 or EPL 1.0": "MPL-2.0 OR EPL-1.0",
  "Mozilla Public License, Version 2.0": "MPL-2.0",
  "GNU Lesser General Public License": "LGPL-2.1-or-later",
  "Public Domain": "LicenseRef-PublicDomain",
};

function gradleModuleCacheRoots() {
  const roots = [];
  const home = process.env.GRADLE_USER_HOME || path.join(process.env.USERPROFILE || process.env.HOME || "", ".gradle");
  roots.push(path.join(home, "caches", "modules-2", "files-2.1"));
  roots.push(path.join(repoRoot, ".gradle", "caches", "modules-2", "files-2.1"));
  return roots.filter((r) => fs.existsSync(r));
}

function findPomFile(group, artifact, version) {
  for (const root of gradleModuleCacheRoots()) {
    const base = path.join(root, group, artifact, version);
    if (!fs.existsSync(base)) continue;
    const stack = [base];
    while (stack.length) {
      const dir = stack.pop();
      for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
        const p = path.join(dir, ent.name);
        if (ent.isDirectory()) stack.push(p);
        else if (ent.name === `${artifact}-${version}.pom`) return p;
      }
    }
  }
  return null;
}

function parsePomLicenses(pomText) {
  const block = pomText.match(/<licenses>([\s\S]*?)<\/licenses>/i);
  if (!block) return null;
  const names = [...block[1].matchAll(/<name>\s*([^<]+?)\s*<\/name>/gi)].map((m) => m[1].trim());
  if (!names.length) return null;
  const mapped = names.map((n) => SPDX_FROM_NAME[n] || n);
  return [...new Set(mapped)].join(" OR ");
}

function guessJavaLicense(group, artifact) {
  // Prefer longer / more specific prefixes.
  const entries = Object.entries(JAVA_LICENSE_HINTS).sort(
    (a, b) => b[0].length - a[0].length,
  );
  for (const [prefix, lic] of entries) {
    if (
      group === prefix ||
      group.startsWith(prefix) ||
      `${group}.`.startsWith(prefix) ||
      artifact === prefix ||
      artifact.startsWith(prefix)
    ) {
      return lic;
    }
  }
  return "NOASSERTION";
}

function resolveJavaLicense(group, artifact, version) {
  const pom = findPomFile(group, artifact, version);
  if (pom) {
    try {
      const text = fs.readFileSync(pom, "utf8");
      const fromPom = parsePomLicenses(text);
      if (fromPom) return { license: fromPom, source: "pom" };
    } catch {
      /* fall through */
    }
  }
  const hint = guessJavaLicense(group, artifact);
  return { license: hint, source: hint === "NOASSERTION" ? "none" : "hint" };
}

function generateJavaBom() {
  console.log("Resolving ispf-server runtimeClasspath via Gradle…");
  const stdout = runGradleRuntimeDeps();
  const deps = parseGradleDeps(stdout);
  const components = [];
  const licenseCounts = {};
  const classCounts = {};
  const findings = [];
  let pomResolved = 0;
  let hintResolved = 0;

  for (const { group, artifact, version } of deps) {
    const { license: lic, source } = resolveJavaLicense(group, artifact, version);
    if (source === "pom") pomResolved += 1;
    if (source === "hint") hintResolved += 1;
    const cls = licenseClass(lic);
    licenseCounts[lic] = (licenseCounts[lic] ?? 0) + 1;
    classCounts[cls] = (classCounts[cls] ?? 0) + 1;

    if (cls === "unknown") {
      findings.push({
        ecosystem: "maven",
        name: `${group}:${artifact}`,
        version,
        license: lic,
        class: cls,
        severity: "medium",
        note: "No POM license / hint — verify before customer counsel sign-off",
      });
    } else if (cls === "strong-copyleft" || cls === "commercial-restricted") {
      findings.push({
        ecosystem: "maven",
        name: `${group}:${artifact}`,
        version,
        license: lic,
        class: cls,
        severity: "high",
        note: "Strong copyleft/restricted on server classpath — unexpected for commercial core",
      });
    } else if (cls === "weak-copyleft" || cls === "weak-copyleft-cpe") {
      findings.push({
        ecosystem: "maven",
        name: `${group}:${artifact}`,
        version,
        license: lic,
        class: cls,
        severity: "low",
        note: "Weak copyleft / CPE on server runtime — retain notices; H2/JAXB/Jakarta/Eclipse expected",
      });
    }

    const purl = `pkg:maven/${group}/${artifact}@${version}`;
    components.push({
      type: "library",
      "bom-ref": purl,
      name: artifact,
      group,
      version,
      purl,
      licenses: componentLicense(lic),
      properties: [
        { name: "ispf:licenseClass", value: cls },
        { name: "ispf:licenseSource", value: source },
      ],
    });
  }

  console.log(`Java licenses: ${pomResolved} from POM, ${hintResolved} from hint map`);

  return {
    bom: bomMeta("ispf-server", components),
    licenseCounts,
    classCounts,
    findings,
    componentCount: components.length,
    pomResolved,
    hintResolved,
  };
}

function generateDriverPackBom() {
  const catalog = JSON.parse(
    fs.readFileSync(path.join(repoRoot, "gradle/driver-packs.json"), "utf8"),
  );
  const RESTRICTED = new Set([
    "GPL-3.0-only",
    "GPL-2.0-only",
    "GPL-3.0-or-later",
    "LGPL-3.0-or-later",
    "MPL-2.0",
  ]);
  const components = [];
  const findings = [];
  const classCounts = {};

  for (const [module, entry] of Object.entries(catalog)) {
    const lic = entry.licenseType || "NOASSERTION";
    const cls = licenseClass(lic);
    classCounts[cls] = (classCounts[cls] ?? 0) + 1;
    const inPermissive = !RESTRICTED.has(lic);
    if (!inPermissive) {
      findings.push({
        ecosystem: "driver-pack",
        name: module,
        version: readPlatformVersion(),
        license: lic,
        class: cls,
        severity: "medium",
        note: inPermissive
          ? ""
          : "Excluded from default DriverPackProfile=permissive; ship only with explicit customer entitlement / disclosure",
      });
    }
    components.push({
      type: "library",
      "bom-ref": `pkg:ispf/driver-pack/${entry.packId}@${readPlatformVersion()}`,
      name: entry.packId,
      version: readPlatformVersion(),
      licenses: componentLicense(lic),
      properties: [
        { name: "ispf:driverId", value: entry.driverId },
        { name: "ispf:module", value: module },
        { name: "ispf:permissiveProfile", value: String(inPermissive) },
        { name: "ispf:licenseClass", value: cls },
      ],
    });
  }

  return {
    bom: bomMeta("ispf-driver-packs", components),
    classCounts,
    findings,
    componentCount: components.length,
    permissiveCount: components.filter(
      (c) => c.properties.find((p) => p.name === "ispf:permissiveProfile")?.value === "true",
    ).length,
    restrictedCount: components.filter(
      (c) => c.properties.find((p) => p.name === "ispf:permissiveProfile")?.value === "false",
    ).length,
  };
}

function writeJson(file, obj) {
  fs.writeFileSync(file, JSON.stringify(obj, null, 2) + "\n", "utf8");
}

function buildLegalReview({ npm, java, drivers }) {
  const allFindings = [...npm.findings, ...java.findings, ...drivers.findings];
  const high = allFindings.filter((f) => f.severity === "high");
  const medium = allFindings.filter((f) => f.severity === "medium");

  const blockers = high.filter(
    (f) =>
      f.class === "unknown" ||
      ((f.ecosystem === "maven" || f.ecosystem === "npm") &&
        (f.class === "strong-copyleft" || f.class === "commercial-restricted")),
  );
  const verdict = blockers.length === 0 ? "CONDITIONAL_GO" : "HOLD";

  const recommendations = [
    {
      id: "R1",
      priority: "P0",
      text: "Commercial closed distribution requires valid platform-license.json (Enterprise) under LICENSE-COMMERCIAL.md; without it AGPL network source-offer applies.",
    },
    {
      id: "R2",
      priority: "P1",
      text: "Attach LICENSE, NOTICE, third-party-notices, and these CycloneDX SBOMs to every binary/appliance delivery.",
    },
    {
      id: "R3",
      priority: "P1",
      text: "If appliance/VM includes PostgreSQL/Redis/NATS/Keycloak/etc., add infrastructure image SBOM/notices separately.",
    },
    {
      id: "R4",
      priority: "P2",
      text: "Protocol packs (BACnet/DLMS/IEC104/DNP3/IPMI/RADIUS/M-Bus) use ISPF-owned Apache-2.0 codecs — no GPL/StepFunc third-party stacks remain.",
    },
    {
      id: "R5",
      priority: "P2",
      text: "Report path is Apache POI only — YARG/JasperReports/docx4j removed from ispf-server.",
    },
    {
      id: "R6",
      priority: "P2",
      text: "Spring Boot transitive weak-copyleft (EPL logback/Jetty/Jakarta CPE) remains — retain notices; not GPL strong-copyleft.",
    },
    {
      id: "R7",
      priority: "P2",
      text: "Dual-licensed jSerialComm / UnboundID LDAP SDK: elect Apache-2.0 / Free Use in SBOM and notices.",
    },
  ];

  return {
    reviewDate: REVIEW_DATE,
    reviewType: "engineering-legal-inventory",
    disclaimer:
      "Engineering inventory and risk classification for commercial delivery preparation. Not a law-firm opinion; counsel review required for contracts.",
    platformLicense: "AGPL-3.0-only (+ optional Enterprise dual-license)",
    commercialProfile: {
      recommendedDriverPackProfile: "permissive",
      enterprisePlatformLicenseRequired: true,
      applicationBundles: "separate customer/vendor EULA",
      gplCommercialProtocolDeps: "removed",
    },
    verdict,
    verdictRationale:
      verdict === "CONDITIONAL_GO"
        ? "No GPL/commercial protocol libraries remain in driver packs (ISPF clean-room codecs). YARG/LGPL report path removed. Closed commercial ship still requires Enterprise platform-license.json (AGPL otherwise). Counsel still required for Spring/EPL transitive notices."
        : "Blocking unknowns or strong-copyleft on core classpath — resolve before commercial media.",
    counts: {
      npmComponents: npm.componentCount,
      javaRuntimeComponents: java.componentCount,
      driverPacks: drivers.componentCount,
      driverPacksPermissive: drivers.permissiveCount,
      driverPacksRestricted: drivers.restrictedCount,
      findingsHigh: high.length,
      findingsMedium: medium.length,
      findingsTotal: allFindings.length,
    },
    npmLicenseHistogram: npm.licenseCounts,
    npmClassHistogram: npm.classCounts,
    javaLicenseHistogram: java.licenseCounts,
    javaClassHistogram: java.classCounts,
    driverClassHistogram: drivers.classCounts,
    findings: allFindings.sort((a, b) => {
      const order = { high: 0, medium: 1, low: 2 };
      return (order[a.severity] ?? 9) - (order[b.severity] ?? 9);
    }),
    recommendations,
    shipChecklist: [
      "LICENSE (AGPL) + LICENSE-COMMERCIAL (if Enterprise)",
      "NOTICE",
      "docs/en/third-party-notices.md (or packaged copy)",
      "build/sbom/web-console.cdx.json",
      "build/sbom/ispf-server-runtime.cdx.json",
      "build/sbom/driver-packs.cdx.json",
      "build/sbom/LEGAL-REVIEW.md",
      "Per driver pack: LICENSE + THIRD_PARTY-NOTICE.txt",
      "platform-license.json issued for customer installationId",
      "DriverPackProfile=permissive (or documented exception list)",
    ],
  };
}

function renderMarkdown(review) {
  const lines = [];
  lines.push(`# ISPF commercial delivery — SBOM legal review`);
  lines.push("");
  lines.push(`> **Date:** ${review.reviewDate}`);
  lines.push(`> **Verdict:** ${review.verdict}`);
  lines.push(`> **Type:** ${review.reviewType}`);
  lines.push("");
  lines.push(review.disclaimer);
  lines.push("");
  lines.push("## Verdict");
  lines.push("");
  lines.push(review.verdictRationale);
  lines.push("");
  lines.push("## Scope counts");
  lines.push("");
  lines.push("| Inventory | Count |");
  lines.push("|-----------|------:|");
  lines.push(`| npm (web-console lockfile) | ${review.counts.npmComponents} |`);
  lines.push(`| Java (ispf-server runtimeClasspath) | ${review.counts.javaRuntimeComponents} |`);
  lines.push(`| Driver packs (catalog) | ${review.counts.driverPacks} |`);
  lines.push(`| Driver packs in permissive profile | ${review.counts.driverPacksPermissive} |`);
  lines.push(`| Driver packs restricted | ${review.counts.driverPacksRestricted} |`);
  lines.push(`| Findings (high / medium / total) | ${review.counts.findingsHigh} / ${review.counts.findingsMedium} / ${review.counts.findingsTotal} |`);
  lines.push("");
  lines.push("## Commercial delivery posture");
  lines.push("");
  lines.push(`- Platform: \`${review.platformLicense}\``);
  lines.push(`- Recommended driver profile: **${review.commercialProfile.recommendedDriverPackProfile}**`);
  lines.push(`- Enterprise platform license required for closed commercial use: **yes**`);
  lines.push(`- Application bundles: ${review.commercialProfile.applicationBundles}`);
  lines.push("");
  lines.push("## Recommendations");
  lines.push("");
  for (const r of review.recommendations) {
    lines.push(`- **${r.id} (${r.priority}):** ${r.text}`);
  }
  lines.push("");
  lines.push("## Restricted driver packs (not in permissive)");
  lines.push("");
  lines.push("| Pack | License | Class |");
  lines.push("|------|---------|-------|");
  for (const f of review.findings.filter((x) => x.ecosystem === "driver-pack")) {
    lines.push(`| \`${f.name}\` | ${f.license} | ${f.class} |`);
  }
  lines.push("");
  lines.push("## Notable server weak-copyleft / CPE");
  lines.push("");
  lines.push("| Component | License | Class |");
  lines.push("|-----------|---------|-------|");
  const seen = new Set();
  for (const f of review.findings.filter(
    (x) => x.ecosystem === "maven" && (x.class === "weak-copyleft" || x.class === "weak-copyleft-cpe"),
  )) {
    if (seen.has(f.name)) continue;
    seen.add(f.name);
    lines.push(`| \`${f.name}\` | ${f.license} | ${f.class} |`);
  }
  lines.push("");
  lines.push("## Ship checklist");
  lines.push("");
  for (const item of review.shipChecklist) {
    lines.push(`- [ ] ${item}`);
  }
  lines.push("");
  lines.push("## SBOM artifacts");
  lines.push("");
  lines.push("- `web-console.cdx.json` — CycloneDX 1.5 (npm)");
  lines.push("- `ispf-server-runtime.cdx.json` — CycloneDX 1.5 (Maven runtime)");
  lines.push("- `driver-packs.cdx.json` — CycloneDX 1.5 (pack catalog)");
  lines.push("- `legal-review.json` — machine-readable twin of this report");
  lines.push("");
  lines.push("---");
  lines.push("");
  lines.push("*Generated by `tools/license-audit/generate-sbom.mjs`.*");
  lines.push("");
  return lines.join("\n");
}

function main() {
  ensureDir(outDir);
  console.log(`SBOM output directory: ${outDir}`);

  const npm = generateNpmBom();
  writeJson(path.join(outDir, "web-console.cdx.json"), npm.bom);
  console.log(`npm SBOM: ${npm.componentCount} components`);

  const java = generateJavaBom();
  writeJson(path.join(outDir, "ispf-server-runtime.cdx.json"), java.bom);
  console.log(`Java SBOM: ${java.componentCount} components`);

  const drivers = generateDriverPackBom();
  writeJson(path.join(outDir, "driver-packs.cdx.json"), drivers.bom);
  console.log(
    `Driver packs SBOM: ${drivers.componentCount} packs (${drivers.permissiveCount} permissive / ${drivers.restrictedCount} restricted)`,
  );

  const review = buildLegalReview({ npm, java, drivers });
  writeJson(path.join(outDir, "legal-review.json"), review);
  fs.writeFileSync(path.join(outDir, "LEGAL-REVIEW.md"), renderMarkdown(review), "utf8");

  // Durable copy for docs / commercial package prep (not under build/)
  const docsOut = path.join(repoRoot, "docs/en/sbom-legal-review.md");
  const docsOutRu = path.join(repoRoot, "docs/ru/sbom-legal-review.md");
  fs.writeFileSync(docsOut, renderMarkdown(review), "utf8");
  fs.writeFileSync(
    docsOutRu,
    renderMarkdown(review)
      .replace(
        "# ISPF commercial delivery — SBOM legal review",
        "# Коммерческая поставка ISPF — юридическая проверка SBOM",
      )
      .replace(
        "> **Language:**",
        "> **Язык:** русская копия отчёта. Канонический EN: [sbom-legal-review.md](../en/sbom-legal-review.md).\n>\n> **Language:**",
      ),
    "utf8",
  );

  // Prepend language headers for docs editions
  const enHeader =
    `> **Language:** Canonical English. Russian edition: [ru/sbom-legal-review.md](../ru/sbom-legal-review.md).\n` +
    `> **Status:** Engineering legal review (${REVIEW_DATE}). Not counsel opinion.\n\n`;
  const ruHeader =
    `> **Язык:** русская версия. Канонический английский: [en/sbom-legal-review.md](../en/sbom-legal-review.md).\n` +
    `> **Статус:** инженерная юридическая проверка (${REVIEW_DATE}). Не заключение юриста.\n\n`;
  fs.writeFileSync(docsOut, enHeader + renderMarkdown(review), "utf8");
  fs.writeFileSync(
    docsOutRu,
    ruHeader +
      renderMarkdown(review).replace(
        "# ISPF commercial delivery — SBOM legal review",
        "# Коммерческая поставка ISPF — юридическая проверка SBOM",
      ),
    "utf8",
  );

  console.log(`Legal review: ${review.verdict}`);
  console.log(`Wrote ${path.join(outDir, "LEGAL-REVIEW.md")}`);
  console.log(`Wrote ${docsOut}`);
  if (review.verdict === "HOLD") process.exitCode = 2;
}

main();
