CREATE INDEX IF NOT EXISTS idx_correlator_hits_lookup
  ON correlator_hits (correlator_id, object_path, event_name, occurred_at DESC);
