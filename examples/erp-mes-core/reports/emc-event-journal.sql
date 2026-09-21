
SELECT e.event_id AS id, e.definition_code, d.name, d.oee_bucket,
       COALESCE(e.job_no, '') AS job_no, COALESCE(e.equipment_id, '') AS equipment_id,
       e.time_min, e.status, e.started_at, e.ended_at
FROM emc_operations_event e
JOIN emc_operations_event_definition d ON d.code = e.definition_code
ORDER BY e.started_at DESC
