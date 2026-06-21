import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { fetchEvents } from "../../api";
import type { ObjectEvent } from "../../types/event";

interface EventJournalPanelProps {
  objectPath?: string;
  limit?: number;
  showFilters?: boolean;
  objectPathFilter?: string;
}

export default function EventJournalPanel({
  objectPath: fixedObjectPath,
  limit = 40,
  showFilters = false,
  objectPathFilter: initialFilter = "",
}: EventJournalPanelProps) {
  const [filterPath, setFilterPath] = useState(initialFilter);
  const objectPath = fixedObjectPath ?? (filterPath.trim() || undefined);

  const events = useQuery({
    queryKey: ["events", objectPath ?? "all", limit],
    queryFn: () => fetchEvents(objectPath, limit),
    refetchInterval: 8000,
  });

  const items = events.data ?? [];

  return (
    <section className="event-journal-panel">
      <header className="event-journal-head">
        <div>
          <h3>Журнал событий</h3>
          {objectPath && <p className="hint">Фильтр: <code>{objectPath}</code></p>}
        </div>
        <span className="badge">{items.length}</span>
      </header>

      {showFilters && !fixedObjectPath && (
        <div className="runtime-journal-filters">
          <label>
            objectPath
            <input
              value={filterPath}
              onChange={(e) => setFilterPath(e.target.value)}
              placeholder="пусто = все объекты"
            />
          </label>
        </div>
      )}

      {events.isLoading && <p className="hint">Загрузка…</p>}
      {events.error && <p className="hint error">Не удалось загрузить журнал</p>}
      {items.length === 0 && !events.isLoading && (
        <p className="hint">Событий пока нет</p>
      )}
      <ul className="event-journal-list">
        {items.map((event) => (
          <EventRow key={event.id} event={event} />
        ))}
      </ul>
    </section>
  );
}

function EventRow({ event }: { event: ObjectEvent }) {
  const payload = event.payload?.rows?.[0];
  const detail =
    payload && typeof payload.value !== "undefined"
      ? `${payload.value}${payload.unit ? ` ${payload.unit}` : ""}`
      : null;

  return (
    <li className={`event-journal-item level-${event.level.toLowerCase()}`}>
      <div className="event-journal-row-top">
        <strong>{event.eventName}</strong>
        <span className="event-level-pill">{event.level}</span>
      </div>
      <p className="hint">{event.objectPath}</p>
      {detail && <p className="event-journal-detail">{detail}</p>}
      <time className="hint event-journal-time">
        {new Date(event.timestamp).toLocaleString()}
      </time>
    </li>
  );
}
