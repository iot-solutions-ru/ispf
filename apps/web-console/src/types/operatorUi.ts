/** Operator application shell — navigation over DASHBOARD objects from the object tree. */

export interface OperatorUiDashboard {
  path: string;
  title: string;
}

export interface OperatorUi {
  appId: string;
  title: string;
  defaultDashboard: string;
  dashboards: OperatorUiDashboard[];
  /** Optional object path filter for operator event journal sidebar. */
  eventJournalObjectPath?: string;
}

export function resolveOperatorDashboard(
  ui: OperatorUi,
  dashboardPath: string | null
): string {
  if (dashboardPath && ui.dashboards.some((item) => item.path === dashboardPath)) {
    return dashboardPath;
  }
  if (ui.defaultDashboard && ui.dashboards.some((item) => item.path === ui.defaultDashboard)) {
    return ui.defaultDashboard;
  }
  return ui.dashboards[0]?.path ?? "";
}
