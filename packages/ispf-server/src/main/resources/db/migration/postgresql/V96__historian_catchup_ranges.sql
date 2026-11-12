-- Durable ClickHouse catch-up ranges (ADR-0035). Sample bytes live in the disk WAL;
-- this table is the range index that survives process restart.
CREATE TABLE IF NOT EXISTS historian_catchup_ranges (
    id BIGSERIAL PRIMARY KEY,
    series_key VARCHAR(1024) NOT NULL,
    from_ts TIMESTAMPTZ NOT NULL,
    to_ts TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS historian_catchup_ranges_series
    ON historian_catchup_ranges (series_key, from_ts);
