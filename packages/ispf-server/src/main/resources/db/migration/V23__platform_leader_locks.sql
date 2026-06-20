CREATE TABLE platform_leader_locks (
    lock_name   VARCHAR(128) PRIMARY KEY,
    holder_id   VARCHAR(64)  NOT NULL,
    expires_at  TIMESTAMP    NOT NULL
);
