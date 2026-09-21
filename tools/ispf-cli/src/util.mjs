import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/** Repo root: tools/ispf-cli/src → ../../.. */
export function repoRoot() {
  return path.resolve(__dirname, "../../..");
}

export function defaultSchemaPath() {
  return path.join(
    repoRoot(),
    "packages/ispf-server/src/main/resources/schema/bundle.schema.json"
  );
}

export function readJsonFile(filePath) {
  return JSON.parse(fs.readFileSync(filePath, "utf8"));
}

export function writeJsonFile(filePath, value) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, `${stableStringify(value)}\n`, "utf8");
}

export function writeTextFile(filePath, text) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, text.endsWith("\n") ? text : `${text}\n`, "utf8");
}

/** Match BundleManifestCanonicalizer / sign-bundle.py: sort map keys only, not list element order. */
export function sortMaps(value) {
  if (value === null || typeof value !== "object") {
    return value;
  }
  if (Array.isArray(value)) {
    return value.map(sortMaps);
  }
  const sorted = {};
  for (const key of Object.keys(value).sort()) {
    sorted[key] = sortMaps(value[key]);
  }
  return sorted;
}

export function stableStringify(value) {
  return JSON.stringify(sortMaps(value), null, 2);
}

export function compactStringify(value) {
  return JSON.stringify(sortMaps(value));
}

export function migrationIdFromFilename(name) {
  const base = name.replace(/\.sql$/i, "");
  const m = /^V\d+__(.+)$/i.exec(base);
  return m ? m[1] : base;
}

export function isBundleJsonPath(p) {
  return path.basename(p).toLowerCase() === "bundle.json";
}

export function resolveInput(inputPath) {
  const abs = path.resolve(inputPath);
  if (!fs.existsSync(abs)) {
    throw new Error(`Path not found: ${abs}`);
  }
  const stat = fs.statSync(abs);
  if (stat.isFile() && isBundleJsonPath(abs)) {
    return { mode: "bundle", path: abs };
  }
  if (stat.isDirectory()) {
    return { mode: "dir", path: abs };
  }
  if (stat.isFile()) {
    return { mode: "bundle", path: abs };
  }
  throw new Error(`Expected directory or bundle.json: ${abs}`);
}

const FILE_REF = /^@file:([^\s]+)$/;

export function inlineFileRefs(obj, baseDir, readFile = (p) => fs.readFileSync(p, "utf8")) {
  if (obj === null || typeof obj !== "object") {
    return obj;
  }
  if (Array.isArray(obj)) {
    return obj.map((item) => inlineFileRefs(item, baseDir, readFile));
  }
  const out = {};
  for (const [key, val] of Object.entries(obj)) {
    if (typeof val === "string") {
      const m = FILE_REF.exec(val.trim());
      if (m) {
        const sqlPath = path.join(baseDir, m[1]);
        if (!fs.existsSync(sqlPath)) {
          throw new Error(`@file reference not found: ${sqlPath}`);
        }
        out[key] = readFile(sqlPath).replace(/\r\n/g, "\n").replace(/\n$/, "");
        continue;
      }
    }
    out[key] = inlineFileRefs(val, baseDir, readFile);
  }
  return out;
}

export function externalizeSqlField(value, functionsDir, basename) {
  if (typeof value !== "string" || value.length === 0) {
    return value;
  }
  if (FILE_REF.test(value.trim())) {
    return value.trim();
  }
  const sqlName = `${basename}.sql`;
  writeTextFile(path.join(functionsDir, sqlName), value);
  return `@file:${sqlName}`;
}
