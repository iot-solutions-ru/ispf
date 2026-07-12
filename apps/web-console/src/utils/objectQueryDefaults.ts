import type { FunctionDescriptor } from "../types";

export const DEFAULT_OBJECT_QUERY_SPEC = JSON.stringify({
  from: {
    alias: "row",
    sourcePathPattern: "root.platform.devices.*",
    objectTypes: ["DEVICE"],
  },
  fields: [{ name: "path", source: "path", alias: "row" }],
  limit: 1000,
});

export function buildObjectQueryRunFunction(specJson: string = DEFAULT_OBJECT_QUERY_SPEC): FunctionDescriptor {
  return {
    name: "run",
    description: "Execute object query (OQ) over platform tree",
    inputSchema: {
      name: "objectQueryInput",
      fields: [{ name: "patch", type: "STRING" }],
    },
    outputSchema: {
      name: "objectQueryOutput",
      fields: [
        { name: "rows", type: "STRING" },
        { name: "rowCount", type: "INTEGER" },
        { name: "patchApplied", type: "BOOLEAN" },
        { name: "patchesApplied", type: "INTEGER" },
      ],
    },
    sourceType: "object-query",
    sourceBody: specJson,
  };
}
