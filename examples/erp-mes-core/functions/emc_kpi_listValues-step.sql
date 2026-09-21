SELECT kpi_code, COALESCE(scope_id, '') AS scope_id, COALESCE(period_label, '') AS period_label, value_num, calculated_at FROM emc_kpi_value WHERE (? = '' OR period_label = ?) ORDER BY kpi_code
