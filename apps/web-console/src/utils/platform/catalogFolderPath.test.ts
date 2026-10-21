import { describe, expect, it } from "vitest";
import { canDeleteObjectPath } from "./platformSystemPaths";
import { resolveCreateDialogMode } from "../object/createObjectMode";

describe("catalog folder paths", () => {
  it("keeps real migration folders protected", () => {
    expect(canDeleteObjectPath("root.platform.migrations", "MIGRATIONS")).toBe(false);
    expect(canDeleteObjectPath("root.platform.applications.demo.migrations", "MIGRATIONS")).toBe(false);
    expect(canDeleteObjectPath("root.tenant.acme.platform.migrations", "MIGRATIONS")).toBe(false);
  });

  it("allows deleting an object that is only named migrations", () => {
    expect(canDeleteObjectPath("root.platform.singleton-blueprints.migrations", "MIGRATIONS")).toBe(true);
    expect(canDeleteObjectPath("root.platform.singleton-blueprints.migrations", "BLUEPRINT")).toBe(true);
    expect(canDeleteObjectPath("root.platform.instance-types.reports", "REPORTS")).toBe(true);
  });

  it("does not open the migration dialog on a singleton named migrations", () => {
    expect(resolveCreateDialogMode("root.platform.migrations")).toBe("migration");
    expect(resolveCreateDialogMode("root.platform.applications.demo.migrations")).toBe("migration");
    expect(resolveCreateDialogMode("root.platform.singleton-blueprints.migrations")).toBe("object");
  });
});
