import { describe, expect, it } from "vitest";
import { buildPlatformBindingExpression, PLATFORM_BINDING_ENTRIES } from "./platformBindings";
import {
  prettyObjectQuerySpec,
  validateObjectQuerySpec,
} from "./objectQuerySpecUtils";
import { DEFAULT_OBJECT_QUERY_SPEC } from "./objectQueryDefaults";

describe("objectQuerySpecUtils", () => {
  it("validates required from.sourcePathPattern", () => {
    expect(validateObjectQuerySpec(DEFAULT_OBJECT_QUERY_SPEC).valid).toBe(true);
    expect(validateObjectQuerySpec('{"fields":[]}').valid).toBe(false);
    expect(validateObjectQuerySpec('{"from":{}}').valid).toBe(false);
  });

  it("pretty-prints JSON spec", () => {
    const pretty = prettyObjectQuerySpec(DEFAULT_OBJECT_QUERY_SPEC);
    expect(pretty).toContain("\n");
    expect(JSON.parse(pretty).from.sourcePathPattern).toBe("root.platform.devices.*");
  });
});

describe("platformBindings oqSpec", () => {
  const queryRows = PLATFORM_BINDING_ENTRIES.find((entry) => entry.id === "queryRows");
  const queryScalar = PLATFORM_BINDING_ENTRIES.find((entry) => entry.id === "queryScalar");

  it("quotes inline JSON spec for queryRows", () => {
    expect(queryRows).toBeDefined();
    const expr = buildPlatformBindingExpression(
      queryRows!,
      { spec: '{"from":{"sourcePathPattern":"root.*"}}' },
      { variableNames: ["oqSpec"] }
    );
    expect(expr).toMatch(/^queryRows\('/);
  });

  it("passes through @/ variable ref for queryScalar", () => {
    expect(queryScalar).toBeDefined();
    const expr = buildPlatformBindingExpression(
      queryScalar!,
      { spec: "@/myOqSpec", aggregate: "count" },
      { variableNames: ["myOqSpec"] }
    );
    expect(expr).toBe('queryScalar(@/myOqSpec, "count")');
  });
});
