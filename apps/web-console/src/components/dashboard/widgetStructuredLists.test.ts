import { describe, expect, it } from "vitest";
import type { SheetConfig } from "../../types/dashboard";
import {
  childWidgetsFromJson,
  childWidgetsToJson,
  popupRowsFromJson,
  popupRowsToJson,
  sheetCellDraftValue,
  sheetCellWithDraft,
  sheetCellWithKind,
  sheetConfigWithCells,
  slidesFromJson,
  slidesToJson,
} from "./widgetStructuredLists";

describe("widget structured lists", () => {
  it("keeps nested widget fields when the list is saved", () => {
    const raw = JSON.stringify([
      { id: "temp", type: "value", title: "Temp", x: 0, y: 0, w: 2, h: 2, variableName: "temperature" },
    ]);
    const next = childWidgetsToJson(
      childWidgetsFromJson(raw).map((child) => ({ ...child, title: "Line" }))
    );
    expect(JSON.parse(next)).toEqual([
      { id: "temp", type: "value", title: "Line", x: 0, y: 0, w: 2, h: 2, variableName: "temperature" },
    ]);
  });

  it("keeps slide children when the label changes", () => {
    const slides = slidesFromJson(
      JSON.stringify([{ id: "s1", label: "One", children: [{ id: "c", type: "label", title: "Hi" }] }])
    );
    slides[0] = { ...slides[0], label: "First" };
    expect(JSON.parse(slidesToJson(slides))[0].children).toEqual([{ id: "c", type: "label", title: "Hi" }]);
  });

  it("stores only named popup rows", () => {
    expect(JSON.parse(popupRowsToJson(popupRowsFromJson(undefined)))).toEqual([]);
    expect(
      JSON.parse(
        popupRowsToJson([
          { name: " reg1 ", label: " Register ", field: " value " },
          { name: "  " },
        ])
      )
    ).toEqual([{ name: "reg1", label: "Register", field: "value" }]);
  });

  it("renames a sheet cell without dropping its validation", () => {
    const config: SheetConfig = {
      rows: 4,
      cols: 2,
      columnFilters: [{ column: "A" }],
      cells: {
        A2: { kind: "input", default: "10", validation: { type: "range", min: 0, max: 10 } },
      },
    };
    const next = sheetConfigWithCells(config, [
      {
        address: "b2",
        cell: sheetCellWithDraft(config.cells.A2, "12"),
      },
    ]);
    expect(next.columnFilters).toEqual([{ column: "A" }]);
    expect(next.cells.B2).toEqual({
      kind: "input",
      default: "12",
      validation: { type: "range", min: 0, max: 10 },
    });
    expect(next.cells.A2).toBeUndefined();
  });

  it("keeps a readonly value in default and moves it when the kind changes", () => {
    expect(sheetCellDraftValue({ kind: "readonly", default: "locked", text: "ignored" })).toBe("locked");
    expect(sheetCellWithDraft({ kind: "readonly" }, "locked")).toEqual({ kind: "readonly", default: "locked" });
    expect(
      sheetCellWithKind(
        { kind: "input", default: "10", validation: { type: "range", min: 0 } },
        "formula"
      )
    ).toEqual({
      kind: "formula",
      expr: "10",
      validation: { type: "range", min: 0 },
    });
  });
});
