export interface ObjectQueryExample {
  id: string;
  tags: string[];
  spec: Record<string, unknown>;
}

export const OBJECT_QUERY_EXAMPLES: ObjectQueryExample[] = [
  {
    id: "deviceInventory",
    tags: ["scan", "devices"],
    spec: {
      from: {
        alias: "row",
        sourcePathPattern: "root.platform.devices.*",
        objectTypes: ["DEVICE"],
      },
      fields: [
        { name: "path", source: "path", alias: "row" },
        { name: "type", source: "type", alias: "row" },
        { name: "displayName", source: "displayName", alias: "row" },
      ],
      orderBy: [{ field: "path", dir: "asc" }],
      limit: 500,
    },
  },
  {
    id: "parentJoin",
    tags: ["join", "parent"],
    spec: {
      from: {
        alias: "row",
        sourcePathPattern: "root.platform.devices.*",
        objectTypes: ["DEVICE"],
      },
      joins: [{ alias: "parent", type: "left", on: { kind: "parent" } }],
      fields: [
        { name: "path", source: "path", alias: "row" },
        { name: "parentPath", source: "path", alias: "parent" },
      ],
      limit: 100,
    },
  },
  {
    id: "groupByType",
    tags: ["aggregate", "groupBy"],
    spec: {
      from: {
        sourcePathPattern: "root.platform.devices.*",
        objectTypes: ["DEVICE"],
      },
      fields: [{ name: "type", source: "type", alias: "row" }],
      groupBy: ["type"],
      aggregates: [{ name: "deviceCount", fn: "count" }],
    },
  },
  {
    id: "variableRefs",
    tags: ["ref", "platformRef"],
    spec: {
      from: {
        alias: "row",
        sourcePathPattern: "root.platform.devices.demo-sensor-01",
        objectTypes: ["DEVICE"],
      },
      fields: [
        { name: "path", source: "path", alias: "row" },
        { name: "temperature", ref: "{row}/temperature/value" },
      ],
    },
  },
  {
    id: "historianColumn",
    tags: ["historian"],
    spec: {
      from: {
        alias: "row",
        sourcePathPattern: "root.platform.devices.*",
        objectTypes: ["DEVICE"],
      },
      fields: [
        { name: "path", source: "path", alias: "row" },
        {
          name: "tempAvg15m",
          ref: "{row}/temperature",
          historian: { fn: "avg", window: "15m" },
        },
      ],
      limit: 50,
    },
  },
  {
    id: "variablesIntrospection",
    tags: ["variables", "introspection"],
    spec: {
      from: {
        alias: "row",
        sourcePathPattern: "root.platform.devices.demo-sensor-01",
        objectTypes: ["DEVICE"],
      },
      fields: [
        { name: "path", source: "path", alias: "row" },
        { name: "variableNames", source: "variables", alias: "row" },
      ],
    },
  },
  {
    id: "recordExpand",
    tags: ["expand", "table"],
    spec: {
      from: {
        alias: "row",
        sourcePathPattern: "root.platform.devices.*",
        objectTypes: ["DEVICE"],
        expand: {
          variable: "ifTable",
          rowKey: "ifIndex",
          filter: "row.ifOperStatus == 2",
        },
      },
      fields: [
        { name: "devicePath", source: "path", alias: "row" },
        { name: "ifIndex", ref: "{row}/ifIndex" },
        { name: "ifDescr", ref: "{row}/ifDescr" },
      ],
      limit: 200,
    },
  },
];

export function findObjectQueryExample(id: string): ObjectQueryExample | undefined {
  return OBJECT_QUERY_EXAMPLES.find((example) => example.id === id);
}
