
SELECT software_id, class_id, name, COALESCE(vendor, '') AS vendor,
       COALESCE(version_label, '') AS version_label, status
FROM emc_software ORDER BY software_id
