
SELECT defect_type_id AS code, COALESCE(description, '') AS name, COALESCE(category, '') AS category
FROM emc_defect_type
ORDER BY defect_type_id
