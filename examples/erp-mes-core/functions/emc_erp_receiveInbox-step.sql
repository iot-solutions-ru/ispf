UPDATE emc_erp_inbox SET status = 'PROCESSED', processed_at = CURRENT_TIMESTAMP WHERE idempotency_key = ?
