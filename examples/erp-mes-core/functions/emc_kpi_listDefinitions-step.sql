SELECT kpi_code, name, COALESCE(iso22400_id, '') AS iso22400_id, COALESCE(unit, '') AS unit, COALESCE(description, '') AS description FROM emc_kpi_definition ORDER BY kpi_code
