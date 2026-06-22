CREATE TABLE agent_sessions (
    session_id   VARCHAR(64)  PRIMARY KEY,
    actor        VARCHAR(128) NOT NULL,
    root_path    VARCHAR(512) NOT NULL,
    title        VARCHAR(256) NOT NULL,
    run_state_json TEXT,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL
);

CREATE INDEX idx_agent_sessions_actor_updated ON agent_sessions (actor, updated_at DESC);

CREATE TABLE agent_turns (
    turn_id            VARCHAR(64) PRIMARY KEY,
    session_id         VARCHAR(64) NOT NULL,
    user_message       TEXT,
    assistant_summary  TEXT,
    status             VARCHAR(16) NOT NULL,
    steps_json         TEXT,
    result_json        TEXT,
    created_at         TIMESTAMP   NOT NULL,
    sort_order         INT         NOT NULL,
    CONSTRAINT fk_agent_turns_session FOREIGN KEY (session_id) REFERENCES agent_sessions(session_id) ON DELETE CASCADE
);

CREATE INDEX idx_agent_turns_session_order ON agent_turns (session_id, sort_order);
