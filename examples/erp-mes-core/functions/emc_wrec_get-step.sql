SELECT section_key, COALESCE(title, '') AS title, COALESCE(content_json, '') AS content_json, updated_at FROM emc_work_record_section WHERE record_id = ? ORDER BY section_key
