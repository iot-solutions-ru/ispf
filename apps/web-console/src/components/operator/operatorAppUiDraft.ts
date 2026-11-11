import type { OperatorAlarmBarConfig } from "../../types/operatorAlarmBar";
import type { OperatorUi, OperatorUiDashboard } from "../../types/operatorUi";

export function buildOperatorAppUi(input: {
  existing?: OperatorUi | null;
  appId: string;
  title: string;
  defaultDashboard: string;
  dashboards: OperatorUiDashboard[];
  alarmBar?: OperatorAlarmBarConfig;
  agentInstructions: string;
  hideTasksAndEvents: boolean;
  hideDashboardNav: boolean;
  hostedUiUrl: string;
  externalSpaUrl: string;
}): OperatorUi | null {
  const title = input.title.trim();
  if (!title || input.dashboards.length === 0) {
    return null;
  }
  const resolvedDefault = input.dashboards.some((item) => item.path === input.defaultDashboard)
    ? input.defaultDashboard
    : input.dashboards[0].path;
  const agentInstructions = input.agentInstructions.trim();
  const hostedUiUrl = input.hostedUiUrl.trim();
  const externalSpaUrl = input.externalSpaUrl.trim();
  return {
    ...input.existing,
    appId: input.appId,
    title,
    defaultDashboard: resolvedDefault,
    dashboards: input.dashboards,
    alarmBar: input.alarmBar,
    agentInstructions: agentInstructions || undefined,
    hostedUiUrl: hostedUiUrl || undefined,
    externalSpaUrl: externalSpaUrl || undefined,
    hideTasksAndEvents: input.hideTasksAndEvents ? true : undefined,
    hideDashboardNav: input.hideDashboardNav ? true : undefined,
  };
}
