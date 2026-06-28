import JSZip from "jszip";
import type ExcelJS from "exceljs";

const SPREADSHEETML = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
const EXTENDED_PROPS =
  "http://schemas.openxmlformats.org/officeDocument/2006/extended-properties";

/** Yandex Sheets / some exporters use prefixed OOXML that ExcelJS cannot parse. */
export function normalizeOoxmlPart(text: string): string {
  let out = text;
  if (out.includes(`xmlns:s="${SPREADSHEETML}"`)) {
    out = out
      .replace(`xmlns:s="${SPREADSHEETML}"`, `xmlns="${SPREADSHEETML}"`)
      .replace(/<(\/?)s:([A-Za-z0-9]+)/g, "<$1$2");
  }
  if (out.includes(`xmlns:ep="${EXTENDED_PROPS}"`)) {
    out = out
      .replace(`xmlns:ep="${EXTENDED_PROPS}"`, `xmlns="${EXTENDED_PROPS}"`)
      .replace(/<(\/?)ep:([A-Za-z0-9]+)/g, "<$1$2")
      .replace(/<(\/?)vt:([A-Za-z0-9]+)/g, "<$1$2");
  }
  return out;
}

function needsNormalization(text: string): boolean {
  return (
    text.includes(`xmlns:s="${SPREADSHEETML}"`) ||
    text.includes(`xmlns:ep="${EXTENDED_PROPS}"`)
  );
}

export async function normalizeXlsxArrayBuffer(input: ArrayBuffer): Promise<ArrayBuffer> {
  const zip = await JSZip.loadAsync(input);
  let changed = false;
  for (const name of Object.keys(zip.files)) {
    const entry = zip.files[name];
    if (entry.dir || !name.endsWith(".xml")) {
      continue;
    }
    const text = await entry.async("string");
    if (!needsNormalization(text)) {
      continue;
    }
    zip.file(name, normalizeOoxmlPart(text));
    changed = true;
  }
  if (!changed) {
    return input;
  }
  return zip.generateAsync({ type: "arraybuffer" });
}

export async function loadXlsxWorkbook(
  ExcelJSModule: typeof ExcelJS,
  file: ArrayBuffer
): Promise<ExcelJS.Workbook> {
  const workbook = new ExcelJSModule.Workbook();
  try {
    await workbook.xlsx.load(file);
    return workbook;
  } catch {
    const normalized = await normalizeXlsxArrayBuffer(file);
    if (normalized === file) {
      throw new Error("XLSX_LOAD_FAILED");
    }
    const retry = new ExcelJSModule.Workbook();
    await retry.xlsx.load(normalized);
    return retry;
  }
}
