-- BL-208: analytics event frames (shift / batch / downtime windows)
CREATE TABLE platform_event_frames (
    frame_id UUID PRIMARY KEY,
    frame_type VARCHAR(32) NOT NULL,
    scope_path VARCHAR(512) NOT NULL,
    source_path VARCHAR(512),
    source_key VARCHAR(256),
    label VARCHAR(256) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP,
    downtime_minutes INTEGER NOT NULL DEFAULT 0,
    metadata_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_event_frames_scope_active
    ON platform_event_frames (scope_path, frame_type, ended_at);

CREATE INDEX idx_event_frames_started
    ON platform_event_frames (started_at DESC);
