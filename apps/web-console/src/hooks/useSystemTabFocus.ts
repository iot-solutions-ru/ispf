import { useMemo } from "react";
import type { AdminClientFocus } from "../context/AdminFocusContext";
import { usePublishAdminFocus } from "./usePublishAdminFocus";

/**
 * Publish System-console tab focus. Pass a memoized `detail` object from the caller
 * (filters, sample rows, form drafts) so Copilot can help on that screen.
 */
export function useSystemTabFocus(
  layerId: string,
  systemTab: string,
  detail: Record<string, unknown>,
  options?: { active?: boolean; priority?: number; screenTitle?: string }
): void {
  const active = options?.active ?? true;
  const priority = options?.priority ?? 65;
  const screenTitle = options?.screenTitle ?? `System › ${systemTab}`;
  const focus = useMemo((): AdminClientFocus => {
    return {
      surface: "system",
      priority,
      detail: {
        screenTitle,
        systemTab,
        ...detail,
      },
    };
  }, [systemTab, detail, priority, screenTitle]);
  usePublishAdminFocus(layerId, focus, active);
}
