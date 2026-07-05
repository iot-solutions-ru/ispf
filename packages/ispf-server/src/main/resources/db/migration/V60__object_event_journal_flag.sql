ALTER TABLE object_nodes
    ADD COLUMN IF NOT EXISTS event_journal_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_object_nodes_event_journal_enabled
    ON object_nodes (event_journal_enabled);
