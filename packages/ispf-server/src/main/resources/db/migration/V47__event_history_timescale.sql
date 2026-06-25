-- Event journal (alert fires, API events). Append-only time series.
-- Timescale hypertable: create_hypertable('event_history', 'occurred_at') at startup when extension is present.

-- idx_event_history_occurred_at (V46) and idx_event_history_object (V1) support time-ordered queries.
