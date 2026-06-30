-- ADR-0020: device measurement time (observed_at) vs platform ingest time (sampled_at).
ALTER TABLE variable_samples
    ADD COLUMN IF NOT EXISTS observed_at TIMESTAMP;

UPDATE variable_samples
SET observed_at = sampled_at
WHERE observed_at IS NULL;

ALTER TABLE variable_samples
    ALTER COLUMN observed_at SET NOT NULL;
