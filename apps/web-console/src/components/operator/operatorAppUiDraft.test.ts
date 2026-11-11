import { describe, expect, it } from "vitest";
import { buildOperatorAppUi } from "./operatorAppUiDraft";

const dashboards = [{ path: "root.platform.dashboards.main", title: "Main" }];

describe("buildOperatorAppUi", () => {
  it("keeps reports, journal path, and work-queue prefix from the loaded document", () => {
    const draft = buildOperatorAppUi({
      existing: {
        appId: "plant",
        title: "Plant",
        defaultDashboard: dashboards[0].path,
        dashboards,
        reports: [{ path: "root.platform.reports.shift", title: "Shift" }],
        defaultReport: "root.platform.reports.shift",
        eventJournalObjectPath: "root.plant.journal",
        workQueueWorkflowPathPrefix: "root.plant.workflows",
        spaNav: [{ route: "/line", label: "Line" }],
        uiPack: { installed: false },
      },
      appId: "plant",
      title: "Plant",
      defaultDashboard: dashboards[0].path,
      dashboards,
      agentInstructions: "",
      hideTasksAndEvents: false,
      hideDashboardNav: false,
      hostedUiUrl: "",
      externalSpaUrl: "",
    });

    expect(draft?.reports).toEqual([{ path: "root.platform.reports.shift", title: "Shift" }]);
    expect(draft?.defaultReport).toBe("root.platform.reports.shift");
    expect(draft?.eventJournalObjectPath).toBe("root.plant.journal");
    expect(draft?.workQueueWorkflowPathPrefix).toBe("root.plant.workflows");
    expect(draft?.spaNav).toEqual([{ route: "/line", label: "Line" }]);
    expect(draft?.uiPack).toEqual({ installed: false });
    expect(draft?.hostedUiUrl).toBeUndefined();
    expect(draft?.externalSpaUrl).toBeUndefined();
    expect(draft?.hideTasksAndEvents).toBeUndefined();
  });

  it("writes trimmed launch URLs and clears a previously true chrome flag", () => {
    const draft = buildOperatorAppUi({
      existing: {
        appId: "plant",
        title: "Plant",
        defaultDashboard: dashboards[0].path,
        dashboards,
        hideTasksAndEvents: true,
        externalSpaUrl: "https://old.example/",
      },
      appId: "plant",
      title: "  Line  ",
      defaultDashboard: dashboards[0].path,
      dashboards,
      agentInstructions: "  cite the SOP  ",
      hideTasksAndEvents: false,
      hideDashboardNav: true,
      hostedUiUrl: "  /apps/plant/  ",
      externalSpaUrl: " https://bridge.example/apps/plant/ ",
    });

    expect(draft).toMatchObject({
      title: "Line",
      agentInstructions: "cite the SOP",
      hostedUiUrl: "/apps/plant/",
      externalSpaUrl: "https://bridge.example/apps/plant/",
      hideDashboardNav: true,
    });
    expect(draft?.hideTasksAndEvents).toBeUndefined();
  });

  it("returns null until a title and at least one dashboard are set", () => {
    expect(
      buildOperatorAppUi({
        appId: "plant",
        title: "  ",
        defaultDashboard: "",
        dashboards,
        agentInstructions: "",
        hideTasksAndEvents: false,
        hideDashboardNav: false,
        hostedUiUrl: "",
        externalSpaUrl: "",
      })
    ).toBeNull();
    expect(
      buildOperatorAppUi({
        appId: "plant",
        title: "Plant",
        defaultDashboard: "",
        dashboards: [],
        agentInstructions: "",
        hideTasksAndEvents: false,
        hideDashboardNav: false,
        hostedUiUrl: "",
        externalSpaUrl: "",
      })
    ).toBeNull();
  });
});
