-- Lease a pending outbox row so two pumps cannot post it.
-- V100 is already applied on the TCK database, so the lease column is a new migration.

DROP INDEX IF EXISTS uq_application_outbox_idempotency;

DELETE FROM application_outbox
WHERE id IN (
    SELECT id FROM (
        SELECT a.id AS id
        FROM application_outbox a
        INNER JOIN application_outbox b
            ON a.app_id = b.app_id
           AND a.connector_id = b.connector_id
           AND a.message_id = b.message_id
           AND a.id <> b.id
        WHERE (b.status = 'SENT' AND a.status <> 'SENT')
           OR (
                CASE WHEN b.status = 'SENT' THEN 1 ELSE 0 END
                    = CASE WHEN a.status = 'SENT' THEN 1 ELSE 0 END
                AND (
                    a.created_at > b.created_at
                    OR (a.created_at = b.created_at AND a.id > b.id)
                )
           )
    ) doomed
);

ALTER TABLE application_outbox ADD COLUMN IF NOT EXISTS claimed_until TIMESTAMP NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_application_outbox_idempotency
    ON application_outbox (app_id, connector_id, message_id);
