CREATE TABLE platform_cluster_replicas (
    replica_id         VARCHAR(64)  PRIMARY KEY,
    version            VARCHAR(32),
    environment        VARCHAR(64),
    java_version       VARCHAR(64),
    started_at         TIMESTAMP    NOT NULL,
    last_heartbeat_at  TIMESTAMP    NOT NULL
);

CREATE INDEX idx_platform_cluster_replicas_heartbeat ON platform_cluster_replicas (last_heartbeat_at);
