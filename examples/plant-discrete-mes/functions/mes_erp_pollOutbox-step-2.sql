UPDATE mes_erp_outbox SET status = 'simulated', processed_at = CURRENT_TIMESTAMP WHERE status = 'pending'
