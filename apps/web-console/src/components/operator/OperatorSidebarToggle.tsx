import { useTranslation } from "react-i18next";
import { useOperatorSidebarCounts } from "../../hooks/useOperatorSidebarCounts";
import type { OperatorUi } from "../../types/operatorUi";

interface OperatorSidebarToggleProps {
  open: boolean;
  onClick: () => void;
  appId?: string;
  ui?: OperatorUi;
}

function formatBadgeCount(count: number): string {
  if (count > 99) {
    return "99+";
  }
  return String(count);
}

export default function OperatorSidebarToggle({
  open,
  onClick,
  appId,
  ui,
}: OperatorSidebarToggleProps) {
  const { t } = useTranslation("operator");
  const { taskCount, eventCount } = useOperatorSidebarCounts(appId, ui);
  const showBadges = !open && (taskCount > 0 || eventCount > 0);

  return (
    <button
      type="button"
      className={`btn operator-sidebar-toggle${showBadges ? " has-badges" : ""}`}
      aria-expanded={open}
      aria-controls="operator-sidebar-panel"
      onClick={onClick}
    >
      <span className="operator-sidebar-toggle-label">
        {open ? t("sidebar.close") : t("sidebar.open")}
      </span>
      {showBadges ? (
        <span className="operator-sidebar-toggle-badges" aria-hidden="true">
          {taskCount > 0 ? (
            <span
              className="operator-sidebar-toggle-badge operator-sidebar-toggle-badge--tasks"
              title={t("sidebar.tasks")}
            >
              {formatBadgeCount(taskCount)}
            </span>
          ) : null}
          {eventCount > 0 ? (
            <span
              className="operator-sidebar-toggle-badge operator-sidebar-toggle-badge--events"
              title={t("sidebar.events")}
            >
              {formatBadgeCount(eventCount)}
            </span>
          ) : null}
        </span>
      ) : null}
      {showBadges ? (
        <span className="sr-only">
          {t("sidebar.badgeSummary", {
            tasks: taskCount,
            events: eventCount,
            defaultValue: `{{tasks}} tasks, {{events}} events`,
          })}
        </span>
      ) : null}
    </button>
  );
}
