
SELECT v.kpi_code, d.name, COALESCE(v.scope_id, '') AS scope_id,
       COALESCE(v.period_label, '') AS period_label, v.value_num,
       CAST(v.calculated_at AS VARCHAR) AS calculated_at
FROM emc_kpi_value v
LEFT JOIN emc_kpi_definition d ON d.kpi_code = v.kpi_code
ORDER BY v.calculated_at DESC
