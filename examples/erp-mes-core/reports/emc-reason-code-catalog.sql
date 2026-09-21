
SELECT reason_code AS code, COALESCE(description, '') AS name
FROM emc_reason_code
ORDER BY reason_code
