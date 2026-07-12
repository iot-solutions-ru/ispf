/**
 * PlatformRef slash grammar (ADR-0043) — TS mirror of com.ispf.core.ref.PlatformRefParser.
 */

export type PlatformRefKind = "variable" | "function" | "event" | "tag";

export interface PlatformRef {
  object: string;
  kind: PlatformRefKind;
  name: string;
  field?: string;
}

const IDENT = /^[A-Za-z_][A-Za-z0-9_]*$/;
const SLASH_FN = /^(@|[A-Za-z_][A-Za-z0-9_.-]*)\/fn\/([A-Za-z_][A-Za-z0-9_-]+)$/;
const SLASH_EVT = /^(@|[A-Za-z_][A-Za-z0-9_.-]*)\/evt\/([A-Za-z_][A-Za-z0-9_-]+)$/;
const SLASH_TAG = /^(@|[A-Za-z_][A-Za-z0-9_.-]*)\/tag\/([A-Za-z_][A-Za-z0-9_-]+)$/;
const SLASH_VAR = /^(@|[A-Za-z_][A-Za-z0-9_.-]*)\/([A-Za-z_][A-Za-z0-9_-]+)(?:\/([A-Za-z_][A-Za-z0-9_]+))?$/;

export function parsePlatformRef(raw: string): PlatformRef | null {
  const trimmed = raw?.trim();
  if (!trimmed) return null;

  let m = trimmed.match(SLASH_FN);
  if (m) return { object: m[1], kind: "function", name: m[2] };

  m = trimmed.match(SLASH_EVT);
  if (m) return { object: m[1], kind: "event", name: m[2] };

  m = trimmed.match(SLASH_TAG);
  if (m) return { object: m[1], kind: "tag", name: m[2] };

  m = trimmed.match(SLASH_VAR);
  if (m) {
    return {
      object: m[1],
      kind: "variable",
      name: m[2],
      field: m[3] ?? "value",
    };
  }

  if (IDENT.test(trimmed)) {
    return { object: "@", kind: "variable", name: trimmed, field: "value" };
  }

  return null;
}

export function formatPlatformRef(ref: PlatformRef): string {
  switch (ref.kind) {
    case "function":
      return `${ref.object}/fn/${ref.name}`;
    case "event":
      return `${ref.object}/evt/${ref.name}`;
    case "tag":
      return `${ref.object}/tag/${ref.name}`;
    case "variable":
    default:
      if (ref.field && ref.field !== "value") {
        return `${ref.object}/${ref.name}/${ref.field}`;
      }
      return `${ref.object}/${ref.name}`;
  }
}

export function refFromFields(
  objectPath: string | undefined,
  name: string | undefined,
  field?: string,
  kind: PlatformRefKind = "variable"
): string | undefined {
  if (!name?.trim()) return undefined;
  const object = !objectPath || objectPath === "self" ? "@" : objectPath.trim();
  if (kind === "function") return formatPlatformRef({ object, kind, name: name.trim() });
  if (kind === "event") return formatPlatformRef({ object, kind, name: name.trim() });
  return formatPlatformRef({
    object,
    kind: "variable",
    name: name.trim(),
    field: field?.trim() || "value",
  });
}

export function fieldsFromRef(refString: string | undefined): {
  objectPath?: string;
  name?: string;
  field?: string;
  kind: PlatformRefKind;
} {
  if (!refString?.trim()) return { kind: "variable" };
  const parsed = parsePlatformRef(refString);
  if (!parsed) return { kind: "variable" };
  return {
    objectPath: parsed.object === "@" ? "self" : parsed.object,
    name: parsed.name,
    field: parsed.field,
    kind: parsed.kind,
  };
}
