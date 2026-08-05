#!/usr/bin/env node
/**
 * BL-183 item 12: offline validate examples/marketplace-catalog listings + bundles.
 * ADR-0054: also validates artifactKind=ui-pack folders (zip + ui-pack.json).
 * Usage: node tools/marketplace-catalog/validate-catalog.mjs
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "../..");
const catalogDir = path.join(root, "examples", "marketplace-catalog");

const REQUIRED_LISTING = [
  "slug",
  "title",
  "description",
  "pricing",
  "appId",
  "vendorName",
  "latestVersion",
  "minIspfVersion",
  "bundleArtifact",
];

const REQUIRED_UI_PACK_LISTING = [
  "slug",
  "title",
  "description",
  "pricing",
  "appId",
  "vendorName",
  "latestVersion",
  "minIspfVersion",
  "bundleArtifact",
  "artifactKind",
];

const SEMVER = /^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$/;

let failures = 0;
let listings = 0;

function fail(msg) {
  failures += 1;
  console.error(`FAIL: ${msg}`);
}

function ok(msg) {
  console.log(`OK: ${msg}`);
}

function readJson(filePath) {
  const raw = fs.readFileSync(filePath, "utf8").replace(/^\uFEFF/, "");
  return JSON.parse(raw);
}

function isUiPackListing(listing) {
  const kind = String(listing?.artifactKind ?? listing?.kind ?? "")
    .trim()
    .toLowerCase()
    .replace(/_/g, "-");
  return kind === "ui-pack";
}

function validateCommonListing(name, listing) {
  if (listing.slug && listing.slug !== name) {
    fail(`${name}: listing.slug '${listing.slug}' must match folder name`);
  }
  if (listing.pricing && !["free", "paid"].includes(listing.pricing)) {
    fail(`${name}: pricing must be free|paid (got ${listing.pricing})`);
  }
  if (listing.latestVersion && !SEMVER.test(String(listing.latestVersion))) {
    fail(`${name}: latestVersion not semver: ${listing.latestVersion}`);
  }
  if (listing.minIspfVersion && !SEMVER.test(String(listing.minIspfVersion))) {
    fail(`${name}: minIspfVersion not semver: ${listing.minIspfVersion}`);
  }
  if (listing.appId == null || String(listing.appId).trim() === "") {
    fail(`${name}: appId required`);
  }
}

function validateApplicationListing(name, dir, listing) {
  const bundlePath = path.join(dir, "bundle.json");
  if (!fs.existsSync(bundlePath)) {
    fail(`${name}: bundle.json missing`);
    return;
  }

  for (const key of REQUIRED_LISTING) {
    if (listing[key] == null || String(listing[key]).trim() === "") {
      fail(`${name}: listing missing required field '${key}'`);
    }
  }

  validateCommonListing(name, listing);

  if (listing.bundleArtifact && listing.bundleArtifact !== "bundle.json") {
    fail(`${name}: bundleArtifact expected bundle.json`);
  }

  let bundle;
  try {
    bundle = readJson(bundlePath);
  } catch (e) {
    fail(`${name}: bundle.json invalid JSON (${e.message})`);
    return;
  }

  if (bundle.version == null || String(bundle.version).trim() === "") {
    fail(`${name}: bundle.version required`);
  } else if (!SEMVER.test(String(bundle.version))) {
    fail(`${name}: bundle.version not semver: ${bundle.version}`);
  }

  if (listing.latestVersion && bundle.version && listing.latestVersion !== bundle.version) {
    fail(
      `${name}: listing.latestVersion (${listing.latestVersion}) != bundle.version (${bundle.version})`
    );
  }

  if (!Array.isArray(bundle.blueprints) && !Array.isArray(bundle.dashboards) && !bundle.operatorUi) {
    if (typeof bundle !== "object" || bundle === null) {
      fail(`${name}: bundle.json is empty`);
    }
  }

  listings += 1;
  ok(`${name} (${listing.appId}@${listing.latestVersion})`);
}

function validateUiPackListing(name, dir, listing) {
  for (const key of REQUIRED_UI_PACK_LISTING) {
    if (listing[key] == null || String(listing[key]).trim() === "") {
      fail(`${name}: listing missing required field '${key}'`);
    }
  }

  validateCommonListing(name, listing);

  const artifact = String(listing.bundleArtifact || "").trim();
  if (!artifact || artifact === "bundle.json") {
    fail(`${name}: ui-pack bundleArtifact must be a .zip file`);
  } else if (!artifact.toLowerCase().endsWith(".zip")) {
    fail(`${name}: ui-pack bundleArtifact must end with .zip (got ${artifact})`);
  } else if (!fs.existsSync(path.join(dir, artifact))) {
    fail(`${name}: ui-pack zip missing: ${artifact}`);
  }

  const manifestPath = path.join(dir, "ui-pack.json");
  if (!fs.existsSync(manifestPath)) {
    fail(`${name}: ui-pack.json missing`);
    return;
  }

  let pack;
  try {
    pack = readJson(manifestPath);
  } catch (e) {
    fail(`${name}: ui-pack.json invalid JSON (${e.message})`);
    return;
  }

  const packAppId = String(pack.appId || pack.id || "").trim();
  if (!packAppId) {
    fail(`${name}: ui-pack.json missing appId/id`);
  } else if (listing.appId && packAppId !== String(listing.appId).trim()) {
    fail(`${name}: listing.appId (${listing.appId}) != ui-pack.json appId (${packAppId})`);
  }

  const packVersion = String(pack.version || "").trim();
  if (!packVersion) {
    fail(`${name}: ui-pack.json version required`);
  } else if (!SEMVER.test(packVersion)) {
    fail(`${name}: ui-pack.json version not semver: ${packVersion}`);
  } else if (listing.latestVersion && listing.latestVersion !== packVersion) {
    fail(
      `${name}: listing.latestVersion (${listing.latestVersion}) != ui-pack.json version (${packVersion})`
    );
  }

  listings += 1;
  ok(`${name} ui-pack (${listing.appId}@${listing.latestVersion})`);
}

if (!fs.existsSync(catalogDir)) {
  console.error(`Catalog directory missing: ${catalogDir}`);
  process.exit(1);
}

const dirs = fs
  .readdirSync(catalogDir, { withFileTypes: true })
  .filter((d) => d.isDirectory())
  .map((d) => d.name)
  .sort();

if (dirs.length < 10) {
  fail(`expected ≥10 listing folders, found ${dirs.length}`);
}

for (const name of dirs) {
  const dir = path.join(catalogDir, name);
  const manifestPath = path.join(dir, "listing.manifest.json");

  if (!fs.existsSync(manifestPath)) {
    fail(`${name}: listing.manifest.json missing`);
    continue;
  }

  let listing;
  try {
    listing = readJson(manifestPath);
  } catch (e) {
    fail(`${name}: listing.manifest.json invalid JSON (${e.message})`);
    continue;
  }

  if (isUiPackListing(listing)) {
    validateUiPackListing(name, dir, listing);
  } else {
    validateApplicationListing(name, dir, listing);
  }
}

console.log(`\nValidated ${listings} marketplace-catalog listings`);
if (failures > 0) {
  console.error(`\n${failures} failure(s)`);
  process.exit(1);
}
if (listings < 10) {
  console.error(`Expected ≥10 valid listings, got ${listings}`);
  process.exit(1);
}
console.log("marketplace-catalog validate OK (BL-183 item 12)");
