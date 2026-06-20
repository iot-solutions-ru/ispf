import { useState } from "react";
import type { OperatorUi } from "../../types/operatorUi";
import WorkQueuePanel from "./WorkQueuePanel";
import EventJournalPanel from "./EventJournalPanel";

interface OperatorSidebarProps {
  operatorId?: string;
  ui?: OperatorUi;
}

type SidebarTab = "tasks" | "events";

export default function OperatorSidebar({ operatorId = "operator", ui }: OperatorSidebarProps) {
  const [tab, setTab] = useState<SidebarTab>("tasks");
  const eventObjectPath = ui?.eventJournalObjectPath;

  return (
    <div className="operator-sidebar-inner">
      <div className="operator-sidebar-tabs">
        <button
          type="button"
          className={`btn small ${tab === "tasks" ? "primary" : ""}`}
          onClick={() => setTab("tasks")}
        >
          Задачи
        </button>
        <button
          type="button"
          className={`btn small ${tab === "events" ? "primary" : ""}`}
          onClick={() => setTab("events")}
        >
          События
        </button>
      </div>
      {tab === "tasks" ? (
        <WorkQueuePanel operatorId={operatorId} />
      ) : (
        <EventJournalPanel objectPath={eventObjectPath} />
      )}
    </div>
  );
}
