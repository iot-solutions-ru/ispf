import { describe, expect, it } from "vitest";
import { recordFieldNames } from "./widgetEditorHelpers";

describe("recordFieldNames", () => {
  it("reads schema fields from the variable payload", () => {
    const names = recordFieldNames(
      [
        {
          name: "temperature",
          value: {
            schema: {
              name: "temperature",
              fields: [
                { name: "value", type: "DOUBLE" },
                { name: "unit", type: "STRING" },
              ],
            },
            rows: [],
          },
        },
      ],
      "temperature",
    );
    expect(names).toEqual(["unit", "value"]);
  });

  it("reads field names from the variable definition schema when value is unset", () => {
    const names = recordFieldNames(
      [
        {
          name: "status",
          schema: {
            name: "status",
            fields: [
              { name: "online", type: "BOOLEAN" },
              { name: "value", type: "STRING" },
            ],
          },
          value: null,
        },
      ],
      "status",
    );
    expect(names).toEqual(["online", "value"]);
  });

  it("falls back to row keys when schema fields are missing", () => {
    const names = recordFieldNames(
      [
        {
          name: "status",
          value: {
            schema: { name: "status", fields: [] },
            rows: [{ online: true, value: "ok" }],
          },
        },
      ],
      "status",
    );
    expect(names).toEqual(["online", "value"]);
  });
});
