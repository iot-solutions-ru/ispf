
SELECT equipment_id AS code, COALESCE(description, '') AS name,
       equipment_level, COALESCE(parent_id, '') AS parent_id
FROM emc_equipment
ORDER BY hierarchy_path
