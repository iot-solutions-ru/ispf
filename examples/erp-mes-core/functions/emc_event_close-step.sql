UPDATE emc_operations_event SET status = 'CLOSED', ended_at = CURRENT_TIMESTAMP WHERE event_id = ? AND status = 'OPEN'
