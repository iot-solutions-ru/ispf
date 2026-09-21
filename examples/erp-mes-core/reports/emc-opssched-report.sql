
SELECT s.schedule_id, s.name, s.state, COALESCE(s.description, '') AS description,
       (SELECT COUNT(*) FROM emc_operations_request r WHERE r.schedule_id = s.schedule_id) AS requests
FROM emc_operations_schedule s ORDER BY s.schedule_id
