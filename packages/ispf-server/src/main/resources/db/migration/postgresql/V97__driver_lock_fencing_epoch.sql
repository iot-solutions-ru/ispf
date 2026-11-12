ALTER TABLE platform_driver_locks
    ADD COLUMN IF NOT EXISTS fencing_epoch BIGINT NOT NULL DEFAULT 0;

UPDATE platform_driver_locks
SET fencing_epoch = 1
WHERE fencing_epoch = 0;

ALTER TABLE platform_driver_locks
    ALTER COLUMN fencing_epoch SET DEFAULT 1;
