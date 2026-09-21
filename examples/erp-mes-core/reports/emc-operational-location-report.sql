
SELECT location_id, COALESCE(description, '') AS description, location_kind,
       COALESCE(equipment_id, '') AS equipment_id, COALESCE(parent_location_id, '') AS parent_location_id,
       status
FROM emc_operational_location ORDER BY location_id
