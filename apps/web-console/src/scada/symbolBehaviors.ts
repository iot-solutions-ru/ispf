import type { MimicBindingSlot, MimicBindingSlotType, MimicSymbolBehavior } from "../types/scadaMimic";

export const BEHAVIOR_TYPES = [
  "text",
  "fill",
  "stroke",
  "visibility",
  "hidden",
  "fillLevel",
  "blink",
] as const;

export type BehaviorType = (typeof BEHAVIOR_TYPES)[number];

export function defaultBehavior(type: BehaviorType): MimicSymbolBehavior {
  switch (type) {
    case "text":
      return { bind: "value", type: "text", target: "#ispf-label", format: "string" };
    case "fillLevel":
      return { bind: "fillLevel", type: "fillLevel", target: "#ispf-fill", maxBind: "maxLevel" };
    case "fill":
      return { bind: "running", type: "fill", target: "#ispf-accent", trueColor: "#3fb950", falseColor: "#484f58" };
    case "stroke":
      return { bind: "running", type: "stroke", target: "#ispf-accent", trueColor: "#3fb950", falseColor: "#484f58" };
    case "visibility":
      return { bind: "visible", type: "visibility", target: "#ispf-accent", when: "truthy" };
    case "hidden":
      return { bind: "hidden", type: "hidden", target: "#ispf-accent", when: "truthy" };
    case "blink":
      return { bind: "alarm", type: "blink", target: "#ispf-accent", when: "truthy" };
  }
}

export function inferBindingType(behavior: MimicSymbolBehavior | undefined): MimicBindingSlotType {
  if (!behavior) return "string";
  switch (behavior.type) {
    case "fill":
    case "stroke":
    case "visibility":
    case "hidden":
    case "blink":
      return "boolean";
    case "fillLevel":
      return "number";
    case "text":
      return behavior.format === "number" ? "number" : "string";
    default:
      return "string";
  }
}

/** Collect bind keys referenced by behaviors (incl. maxBind / qualityBind). */
export function behaviorBindingKeys(behaviors: MimicSymbolBehavior[]): string[] {
  const keys = new Set<string>();
  for (const b of behaviors) {
    keys.add(b.bind);
    if (b.type === "text" && b.qualityBind?.trim()) keys.add(b.qualityBind.trim());
    if (b.type === "fillLevel" && b.maxBind?.trim()) keys.add(b.maxBind.trim());
  }
  return [...keys];
}

export function syncBindingSchemaFromBehaviors(
  behaviors: MimicSymbolBehavior[],
  existing?: MimicBindingSlot[]
): MimicBindingSlot[] {
  const existingMap = new Map((existing ?? []).map((s) => [s.key, s]));
  const primaryBinds = new Set(behaviors.map((b) => b.bind));

  return behaviorBindingKeys(behaviors).map((key) => {
    const prev = existingMap.get(key);
    if (prev) return prev;
    const behavior = behaviors.find((b) => b.bind === key);
    return {
      key,
      labelKey: `bindings.${key}`,
      type: inferBindingType(behavior),
      optional: !primaryBinds.has(key),
    };
  });
}

/** SVG element ids for behavior target picker (`#id`). */
export function extractSvgTargetIds(svg: string): string[] {
  const ids = new Set<string>();
  const re = /\bid=["']([^"']+)["']/gi;
  let match: RegExpExecArray | null;
  while ((match = re.exec(svg))) {
    ids.add(`#${match[1]}`);
  }
  return [...ids].sort();
}
