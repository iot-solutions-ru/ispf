import { describe, expect, it } from "vitest";
import {
  isApplicationSqlCatalogLeaf,
  isPlatformSqlObjectPath,
  isSqlBindingPath,
} from "./platformSqlPath";

describe("platformSqlPath application catalog leaves", () => {
  it("recognizes app bindings, migrations, schedules leaves", () => {
    const binding = "root.platform.applications.warehouse.bindings.sync-count";
    const migration = "root.platform.applications.warehouse.migrations.v1";
    const schedule = "root.platform.applications.warehouse.schedules.nightly";
    expect(isApplicationSqlCatalogLeaf(binding)).toBe(true);
    expect(isApplicationSqlCatalogLeaf(migration)).toBe(true);
    expect(isApplicationSqlCatalogLeaf(schedule)).toBe(true);
    expect(isPlatformSqlObjectPath(binding)).toBe(true);
  });

  it("does not treat app roots or non-sql subfolders as sql catalog leaves", () => {
    expect(isApplicationSqlCatalogLeaf("root.platform.applications.warehouse")).toBe(false);
    expect(isApplicationSqlCatalogLeaf("root.platform.applications.warehouse.reports.r1")).toBe(false);
    expect(isApplicationSqlCatalogLeaf("root.platform.applications.warehouse.functions.fn")).toBe(false);
    expect(isPlatformSqlObjectPath("root.platform.applications.warehouse.reports.r1")).toBe(false);
  });

  it("keeps platform bindings under root.platform.bindings", () => {
    const path = "root.platform.bindings.testBinding";
    expect(isSqlBindingPath(path)).toBe(true);
    expect(isPlatformSqlObjectPath(path)).toBe(true);
  });
});
