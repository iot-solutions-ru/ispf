ALTER TABLE object_variables
    ADD COLUMN history_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE object_variables
    ADD COLUMN history_retention_days INTEGER;

-- Backfill known telemetry variables created before per-variable history metadata.
UPDATE object_variables
SET history_enabled = TRUE
WHERE name IN (
    'temperature',
    'sysUpTime',
    'hrMemorySize',
    'hrSystemProcesses',
    'hrSystemNumUsers',
    'ifNumber',
    'ifInOctets',
    'ifOutOctets',
    'hrProcessorLoad'
);
