import { useState } from "react";
import SystemMetricsView from "./SystemMetricsView";
import EventJournalPanel from "./operator/EventJournalPanel";
import FunctionInvokeJournalPanel from "./runtime/FunctionInvokeJournalPanel";

type SystemTab = "metrics" | "events" | "functions";

const TABS: { id: SystemTab; label: string }[] = [
  { id: "metrics", label: "Метрики" },
  { id: "events", label: "Журнал событий" },
  { id: "functions", label: "Журнал функций" },
];

export default function SystemView() {
  const [tab, setTab] = useState<SystemTab>("metrics");

  return (
    <main className="main system-view">
      <header className="system-metrics-header">
        <div>
          <h2>Система</h2>
          <p className="op-muted">
            Метрики платформы, журнал опубликованных событий и audit вызовов функций.
          </p>
        </div>
      </header>

      <nav className="system-subnav">
        {TABS.map((item) => (
          <button
            key={item.id}
            type="button"
            className={tab === item.id ? "active" : ""}
            onClick={() => setTab(item.id)}
          >
            {item.label}
          </button>
        ))}
      </nav>

      {tab === "metrics" && <SystemMetricsView embedded />}
      {tab === "events" && (
        <EventJournalPanel limit={100} showFilters objectPathFilter="" />
      )}
      {tab === "functions" && <FunctionInvokeJournalPanel limit={100} showFilters />}
    </main>
  );
}
