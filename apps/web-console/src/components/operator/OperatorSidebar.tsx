import { useState } from "react";
import WorkQueuePanel from "./WorkQueuePanel";
import EventJournalPanel from "./EventJournalPanel";

const DEMO_DEVICE = "root.platform.devices.demo-sensor-01";

interface OperatorSidebarProps {
  operatorId?: string;
}

type SidebarTab = "tasks" | "events";

export default function OperatorSidebar({ operatorId = "operator" }: OperatorSidebarProps) {
  const [tab, setTab] = useState<SidebarTab>("tasks");

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
        <EventJournalPanel objectPath={DEMO_DEVICE} />
      )}
    </div>
  );
}
