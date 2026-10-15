import { describe, expect, it } from "vitest";
import type { DashboardWidget } from "../../types/dashboard";
import { collectDashboardSessionParams } from "./sessionParams";

describe("collectDashboardSessionParams", () => {
  it("collects session param names from both sides of the maps that store them", () => {
    const widgets = [
      { type: "label", paramKey: " headline " },
      { type: "value", contextPathKey: "devicePath" },
      {
        type: "function-form",
        paramBindingsJson: JSON.stringify({ file_name: "fileName" }),
        syncFieldsToSessionJson: JSON.stringify({ qty: "quantity" }),
        requireSessionParamsJson: JSON.stringify(["fileName", " "]),
        clearSessionParamsJson: JSON.stringify(["draft"]),
        fieldsJson: JSON.stringify([{ name: "code", paramKey: "itemCode" }]),
      },
      {
        type: "report",
        contextParamsJson: JSON.stringify({ status: "dispatchStatus" }),
        rowParamsFromRowJson: JSON.stringify({ orderId: "id" }),
        parametersJson: JSON.stringify({ limit: "20" }),
      },
      {
        type: "dashboard-link",
        contextParamsJson: JSON.stringify({ zone: "hall" }),
      },
      { type: "object-table", rowParamsJson: JSON.stringify({ line: "A" }) },
      { type: "card-grid", cardParamsJson: JSON.stringify({ pump: "p1" }) },
    ] as DashboardWidget[];

    expect(collectDashboardSessionParams(widgets)).toEqual([
      "devicePath",
      "dispatchStatus",
      "draft",
      "fileName",
      "headline",
      "itemCode",
      "line",
      "orderId",
      "pump",
      "quantity",
      "zone",
    ]);
  });
});
