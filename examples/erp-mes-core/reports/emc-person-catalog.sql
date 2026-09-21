
SELECT person_id AS code, person_name AS name, COALESCE(personnel_class_id, '') AS personnel_class_id
FROM emc_person
ORDER BY person_id
