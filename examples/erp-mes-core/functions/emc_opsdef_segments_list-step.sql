SELECT definition_id, version, segment_id, sequence_no FROM emc_operations_definition_segment WHERE COALESCE(NULLIF(TRIM(?), ''), definition_id) = definition_id ORDER BY definition_id, sequence_no
