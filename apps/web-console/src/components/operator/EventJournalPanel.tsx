import { useQuery } from "@tanstack/react-query";
import { fetchEvents } from "../../api";
import type { ObjectEvent } from "../../types/event";

interface EventJournalPanelProps {
  objectPath?: string;
  limit?: number;
}

export default function EventJournalPanel({
  objectPath,
  limit = 40,
}: EventJournalPanelProps) {
  const events = useQuery({
    queryKey: ["events", objectPath ?? "all", limit],
    queryFn: () => fetchEvents(objectPath, limit),
    refetchInterval: 8000,
  });

  const items = events.data ?? [];

  return (
    <section className="event-journal-panel">
      <header className="event-journal-head">
        <h3>Журнал событий</h3>
        <span className="badge">{items.length}</span>
      </header>
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
