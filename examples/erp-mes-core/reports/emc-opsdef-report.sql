
SELECT definition_id, version, name, COALESCE(description, '') AS description,
       published_flag, status,
       (SELECT COUNT(*) FROM emc_operations_definition_segment s
        WHERE s.definition_id = d.definition_id AND s.version = d.version) AS segments
FROM emc_operations_definition d ORDER BY definition_id, version
