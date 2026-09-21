
SELECT definition_id AS code, COALESCE(description, '') AS name,
       COALESCE(class_id, '') AS class_id, kind, base_uom
FROM emc_material_definition
ORDER BY definition_id
