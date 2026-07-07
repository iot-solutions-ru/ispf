/** Brick Schema namespace — mirrors {@link BrickExportService#BRICK_NS}. */
export const BRICK_NS = "https://brickschema.org/schema/Brick#";

export type BrickConfidence = "high" | "medium" | "low";

export interface BrickClassSuggestion {
  brickClass: string;
  compactClass: string;
  confidence: BrickConfidence;
  reason: string;
}

export function resolveBrickClass(raw: string): string {
  const trimmed = raw.trim();
  if (!trimmed) {
    return `${BRICK_NS}Sensor`;
  }
  if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("urn:")) {
    return trimmed;
  }
  if (trimmed.includes(":")) {
    if (trimmed.startsWith("brick:")) {
      return BRICK_NS + trimmed.slice("brick:".length);
    }
    return trimmed;
  }
  return BRICK_NS + trimmed;
}

export function compactBrickClass(typeIri: string): string {
  if (typeIri.startsWith(BRICK_NS)) {
    return `brick:${typeIri.slice(BRICK_NS.length)}`;
  }
  return typeIri;
}

function suggestion(brickLocalName: string, confidence: BrickConfidence, reason: string): BrickClassSuggestion {
  const iri = resolveBrickClass(`brick:${brickLocalName}`);
  return {
    brickClass: iri,
    compactClass: compactBrickClass(iri),
    confidence,
    reason,
  };
}

function addIfAbsent(suggestions: BrickClassSuggestion[], candidate: BrickClassSuggestion | null): void {
  if (!candidate) {
    return;
  }
  if (!suggestions.some((item) => item.brickClass === candidate.brickClass)) {
    suggestions.push(candidate);
  }
}

function matchByTag(
  tags: Set<string>,
  marker: string,
  brickLocalName: string,
  confidence: BrickConfidence,
  reason: string
): BrickClassSuggestion | null {
  if (!tags.has(marker)) {
    return null;
  }
  return suggestion(brickLocalName, confidence, reason);
}

function normalizeTags(tags: Iterable<string> | undefined): Set<string> {
  const normalized = new Set<string>();
  if (!tags) {
    return normalized;
  }
  for (const tag of tags) {
    const trimmed = tag.trim().toLowerCase();
    if (trimmed) {
      normalized.add(trimmed);
    }
  }
  return normalized;
}

/**
 * Client-side Brick class hints from Haystack tags — lightweight mirror of server inference rules.
 */
export function inferBrickClassHints(params: {
  haystackTags?: Iterable<string>;
  pointHaystackTags?: Iterable<string>;
  haystackKind?: string;
  displayName?: string;
  objectPath?: string;
}): BrickClassSuggestion[] {
  const haystackTags = normalizeTags(params.haystackTags);
  const pointTags = normalizeTags(params.pointHaystackTags);
  const allTags = new Set([...haystackTags, ...pointTags]);
  const haystackKind = (params.haystackKind ?? "").trim();
  const hay = `${params.displayName ?? ""} ${params.objectPath ?? ""}`.toLowerCase();

  const suggestions: BrickClassSuggestion[] = [];

  addIfAbsent(
    suggestions,
    matchByTag(allTags, "ahu", "Air_Handler_Unit", "high", "Haystack tag or name suggests air handling unit")
  );
  addIfAbsent(
    suggestions,
    matchByTag(allTags, "vav", "Variable_Air_Volume_Box", "high", "Haystack tag suggests VAV terminal")
  );
  addIfAbsent(suggestions, matchByTag(allTags, "meter", "Meter", "high", "Haystack tag suggests metering equipment"));
  addIfAbsent(
    suggestions,
    matchByTag(allTags, "chiller", "Chiller", "high", "Haystack tag suggests chiller plant equipment")
  );
  addIfAbsent(
    suggestions,
    matchByTag(allTags, "boiler", "Boiler", "high", "Haystack tag suggests boiler equipment")
  );
  addIfAbsent(suggestions, matchByTag(allTags, "pump", "Pump", "high", "Haystack tag suggests pump equipment"));
  addIfAbsent(suggestions, matchByTag(allTags, "fan", "Fan", "high", "Haystack tag suggests fan equipment"));
  addIfAbsent(
    suggestions,
    matchByTag(allTags, "co2", "CO2_Sensor", "medium", "Point or equip tags include CO2")
  );
  addIfAbsent(
    suggestions,
    matchByTag(allTags, "humidity", "Humidity_Sensor", "medium", "Point or equip tags include humidity")
  );
  addIfAbsent(
    suggestions,
    matchByTag(allTags, "temp", "Temperature_Sensor", "medium", "Point or equip tags include temperature")
  );

  if (hay.includes("ahu") || hay.includes("air-handler")) {
    addIfAbsent(
      suggestions,
      suggestion("Air_Handler_Unit", "high", "Device name/path suggests air handling unit")
    );
  }
  if (hay.includes("meter") || hay.includes("kwh") || hay.includes("energy")) {
    addIfAbsent(suggestions, suggestion("Meter", "medium", "Device name/path suggests metering"));
  }

  if (haystackKind.toLowerCase() === "site") {
    addIfAbsent(
      suggestions,
      suggestion("Building", "medium", "haystackKind=site maps to Brick Building")
    );
  }

  if (allTags.has("equip") && allTags.has("sensor")) {
    addIfAbsent(suggestions, suggestion("Sensor", "medium", "equip + sensor Haystack markers"));
  } else if (allTags.has("sensor") || allTags.has("point")) {
    addIfAbsent(suggestions, suggestion("Sensor", "low", "Generic sensor/point Haystack markers"));
  } else if (haystackKind.toLowerCase() === "equip" || allTags.has("equip")) {
    addIfAbsent(suggestions, suggestion("Equipment", "low", "Generic equipment Haystack marker"));
  }

  if (suggestions.length === 0) {
    suggestions.push(
      suggestion("Sensor", "low", "Default Brick class when no stronger Haystack signal is present")
    );
  }

  return suggestions;
}
