SELECT network_id, name, COALESCE(description, '') AS description, COALESCE(hierarchy_scope_id, '') AS hierarchy_scope_id, status FROM emc_resource_relationship_network ORDER BY network_id
