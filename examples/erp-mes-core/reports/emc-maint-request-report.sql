
SELECT r.request_id, r.equipment_id, COALESCE(r.description, '') AS description,
       r.priority, r.status,
       COALESCE((SELECT w.wo_id FROM emc_maintenance_work_order w
                 WHERE w.request_id = r.request_id LIMIT 1), '') AS wo_id,
       CAST(r.created_at AS VARCHAR) AS created_at
FROM emc_maintenance_request r ORDER BY r.created_at DESC
