SELECT id::text AS shift_id, line_code, shift_label, planned_minutes, downtime_minutes, total_units, good_units FROM mes_oee_shift ORDER BY line_code, shift_label
