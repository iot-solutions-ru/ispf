SELECT scope_id, name, COALESCE(parent_scope_id, '') AS parent_scope_id, COALESCE(description, '') AS description FROM emc_hierarchy_scope ORDER BY scope_id
