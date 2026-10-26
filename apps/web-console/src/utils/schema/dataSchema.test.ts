import { describe, expect, it } from "vitest";
import { alignRecordToSchema, emptySchema, newSchemaField, syncRecordSchema } from "./dataSchema";

describe("dataSchema", () => {
  it("syncRecordSchema aligns rows with new fields", () => {
    const schema = {
      name: "test",
      fields: [
        { name: "a", type: "STRING" },
        { name: "b", type: "INTEGER" },
      ],
    };
    const record = syncRecordSchema(
      { schema: emptySchema("test"), rows: [{ a: "x" }] },
      schema
    );
    expect(record.schema.fields).toHaveLength(2);
    expect(record.rows[0]).toEqual({ a: "x", b: 0 });
  });

  it("alignRecordToSchema keeps same-type cells and clears the rest", () => {
    const previous = {
      name: "reading",
      fields: [
        { name: "value", type: "DOUBLE" },
        { name: "note", type: "STRING" },
      ],
    };
    const next = {
      name: "reading",
      fields: [
        { name: "value", type: "DOUBLE" },
        { name: "unit", type: "STRING" },
      ],
    };
    const record = alignRecordToSchema(
      { schema: previous, rows: [{ value: 1.5, note: "hot" }] },
      next,
      previous
    );
    expect(record.rows[0]).toEqual({ value: 1.5, unit: null });
  });

  it("newSchemaField defaults to STRING nullable", () => {
    const field = newSchemaField("foo");
    expect(field.type).toBe("STRING");
    expect(field.nullable).toBe(true);
  });
});
