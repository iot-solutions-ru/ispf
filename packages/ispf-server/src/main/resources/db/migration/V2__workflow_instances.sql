-- Workflow v2: persistent instances and operator work queue

CREATE TABLE IF NOT EXISTS workflow_instances (
    id              VARCHAR(64)  PRIMARY KEY,
    workflow_path   VARCHAR(512) NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    current_node_id VARCHAR(128),
    assignee        VARCHAR(128),
    trigger_object_path VARCHAR(512),
    state_json      TEXT,
    started_at      TIMESTAMP    NOT NULL,
    completed_at    TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS workflow_user_tasks (
    id              VARCHAR(64)  PRIMARY KEY,
    instance_id     VARCHAR(64)  NOT NULL,
    workflow_path   VARCHAR(512) NOT NULL,
    task_node_id    VARCHAR(128) NOT NULL,
    title           VARCHAR(256) NOT NULL,
    instructions    TEXT,
    assignee_role   VARCHAR(64)  NOT NULL DEFAULT 'operator',
    status          VARCHAR(16)  NOT NULL,
    assignee        VARCHAR(128),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed_at      TIMESTAMP,
    completed_at    TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_workflow_instances_path ON workflow_instances(workflow_path, status);
CREATE INDEX IF NOT EXISTS idx_workflow_user_tasks_status ON workflow_user_tasks(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_workflow_user_tasks_instance ON workflow_user_tasks(instance_id);
