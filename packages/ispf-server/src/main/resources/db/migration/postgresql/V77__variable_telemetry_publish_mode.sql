ALTER TABLE object_variables
    ADD COLUMN IF NOT EXISTS telemetry_publish_mode VARCHAR(32);
