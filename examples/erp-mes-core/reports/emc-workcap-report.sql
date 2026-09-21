
SELECT capability_id, name, COALESCE(description, '') AS description,
       COALESCE(hierarchy_scope_id, '') AS hierarchy_scope_id, status
FROM emc_work_capability ORDER BY capability_id
