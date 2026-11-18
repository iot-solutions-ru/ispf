import { describe, expect, it } from "vitest";
import { isSqlIdent, parseTableNodeKey, previewSelect, quoteIdent, rowsToCsv, tableNodeKey } from "./dataSourceBrowserSql";

describe("dataSourceBrowserSql", () => {
  it("quotes identifiers and doubles an embedded quote", () => {
    expect(quoteIdent("items")).toBe('"items"');
    expect(quoteIdent('a"b')).toBe('"a""b"');
    expect(quoteIdent("orders", "`")).toBe("`orders`");
    expect(quoteIdent("orders", "[")).toBe("[orders]");
  });

  it("builds a qualified preview select", () => {
    expect(previewSelect("app_demo", "items")).toBe('SELECT * FROM "app_demo"."items"');
    expect(previewSelect(null, "items", "`")).toBe("SELECT * FROM `items`");
  });

  it("round-trips table node keys that contain a pipe", () => {
    const key = tableNodeKey("app|demo", null, "app_demo", "items");
    expect(parseTableNodeKey(key)).toEqual({
      schemaName: "app|demo",
      catalog: null,
      schema: "app_demo",
      table: "items",
    });
  });

  it("escapes csv cells", () => {
    expect(rowsToCsv(["a", "b"], [{ a: 'say "hi"', b: null }])).toBe('a,b\n"say ""hi""",');
  });

  it("accepts only sql identifiers for generated ddl", () => {
    expect(isSqlIdent("items_1")).toBe(true);
    expect(isSqlIdent("bad-name")).toBe(false);
    expect(isSqlIdent("")).toBe(false);
  });
});
