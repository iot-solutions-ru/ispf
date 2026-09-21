
SELECT container_id, class_id, name, COALESCE(description, '') AS description,
       COALESCE(hierarchy_scope_id, '') AS hierarchy_scope_id,
       COALESCE(CAST(capacity AS VARCHAR), '') AS capacity,
       COALESCE(capacity_uom, '') AS capacity_uom, status
FROM emc_container ORDER BY container_id
