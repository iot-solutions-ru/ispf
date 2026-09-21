SELECT report_id, title, report_group, period_label, format, updated_at::text AS updated_at, row_count, owner_name, status, summary FROM oc_kpi_report ORDER BY updated_at DESC
