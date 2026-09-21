UPDATE emc_work_alert SET status = 'ACKNOWLEDGED', ack_by = ?, ack_at = CURRENT_TIMESTAMP WHERE alert_id = ?
