
SELECT alert_id, alert_type, severity, COALESCE(work_master_id, '') AS work_master_id,
       message, status, CAST(raised_at AS VARCHAR) AS raised_at,
       COALESCE(ack_by, '') AS ack_by
FROM emc_work_alert ORDER BY raised_at DESC
