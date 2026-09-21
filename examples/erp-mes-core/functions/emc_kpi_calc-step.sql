INSERT INTO emc_kpi_value (id, kpi_code, scope_id, period_label, value_num) VALUES (gen_random_uuid(), 'MTBF', NULLIF(?, ''), ?, COALESCE(?, 0))
