import type { ReactNode } from "react";
import { DashboardEditorContext } from "./useDashboardEditor";
import type { DashboardEditorContextValue } from "./useDashboardEditor";

export function DashboardEditorProvider({
  value,
  children,
}: {
  value: DashboardEditorContextValue;
  children: ReactNode;
}) {
  return <DashboardEditorContext.Provider value={value}>{children}</DashboardEditorContext.Provider>;
}

