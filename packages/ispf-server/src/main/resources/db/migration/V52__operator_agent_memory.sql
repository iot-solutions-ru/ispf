-- Long-lived knowledge per operator application (shared across operators of the app).
CREATE TABLE operator_agent_memory (
    memory_id     VARCHAR(64)  PRIMARY KEY,
    app_id        VARCHAR(128) NOT NULL,
    kind          VARCHAR(32)  NOT NULL,
    topic         VARCHAR(256) NOT NULL,
    content       TEXT         NOT NULL,
    source_actor  VARCHAR(128),
    source_turn_id VARCHAR(64),
    use_count     INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    CONSTRAINT uq_operator_agent_memory_app_topic UNIQUE (app_id, topic)
);

CREATE INDEX idx_operator_agent_memory_app_updated ON operator_agent_memory (app_id, updated_at DESC);
