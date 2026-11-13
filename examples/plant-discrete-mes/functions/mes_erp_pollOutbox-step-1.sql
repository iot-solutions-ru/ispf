SELECT id::text AS outbox_id, entity_type, entity_id, idempotency_key, status FROM mes_erp_outbox ORDER BY created_at DESC LIMIT 50
