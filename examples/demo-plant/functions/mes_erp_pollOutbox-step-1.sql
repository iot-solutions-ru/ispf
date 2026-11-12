SELECT id::text AS outbox_id, entity_type, entity_id, idempotency_key FROM mes_erp_outbox WHERE status = 'pending' ORDER BY created_at LIMIT 50
