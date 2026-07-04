CREATE TABLE platform_driver_locks (
    device_path VARCHAR(512) PRIMARY KEY,
    holder_id   VARCHAR(64)  NOT NULL,
    expires_at  TIMESTAMP    NOT NULL
);

CREATE INDEX idx_platform_driver_locks_expires ON platform_driver_locks (expires_at);
