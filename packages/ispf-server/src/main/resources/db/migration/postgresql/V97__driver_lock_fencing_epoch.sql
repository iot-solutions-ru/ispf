ALTER TABLE platform_driver_locks
    ADD COLUMN IF NOT EXISTS fencing_epoch BIGINT NOT NULL DEFAULT 0;
