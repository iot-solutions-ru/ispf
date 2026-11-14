-- One platform outbox row per app, connector, and message id.
-- A repeated enqueue returns that row instead of posting a second delivery.

CREATE UNIQUE INDEX IF NOT EXISTS uq_application_outbox_idempotency
    ON application_outbox (app_id, connector_id, message_id);
