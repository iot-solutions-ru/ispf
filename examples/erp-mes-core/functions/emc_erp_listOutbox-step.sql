SELECT verb, noun, COALESCE(object_id, '') AS object_id, status, COALESCE(ack_code, '') AS ack_code, idempotency_key, created_at FROM emc_erp_outbox ORDER BY created_at DESC
