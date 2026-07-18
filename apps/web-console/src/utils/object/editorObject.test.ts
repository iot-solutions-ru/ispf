import { describe, expect, it } from "vitest";
import { isSpecializedEditorObject, resolveEditorObjectType } from "./editorObject";

describe("editorObject APPLICATION", () => {
  it("treats application root nodes as specialized editors", () => {
    const path = "root.platform.applications.warehouse";
    expect(isSpecializedEditorObject(path, "APPLICATION")).toBe(true);
    expect(resolveEditorObjectType(path, "APPLICATION")).toBe("APPLICATION");
  });

  it("does not treat application child folders as specialized editors", () => {
    const path = "root.platform.applications.warehouse.reports";
    expect(isSpecializedEditorObject(path, "REPORTS")).toBe(false);
    expect(resolveEditorObjectType(path, "REPORTS")).toBe("REPORTS");
  });
});
