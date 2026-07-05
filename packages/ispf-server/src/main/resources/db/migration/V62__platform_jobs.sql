CREATE TABLE platform_jobs (
    job_id         UUID         PRIMARY KEY,
    job_type       VARCHAR(64)  NOT NULL,
    status         VARCHAR(32)  NOT NULL,
    priority       INT          NOT NULL DEFAULT 0,
    payload        VARCHAR(8192) NOT NULL,
    result         VARCHAR(1048576),
    error_message  VARCHAR(4096),
    holder_id      VARCHAR(64),
    expires_at     TIMESTAMP,
    created_at     TIMESTAMP    NOT NULL,
    started_at     TIMESTAMP,
    completed_at   TIMESTAMP,
    created_by     VARCHAR(128)
);

CREATE INDEX idx_platform_jobs_status_created ON platform_jobs (status, created_at);
CREATE INDEX idx_platform_jobs_expires_at ON platform_jobs (expires_at);
