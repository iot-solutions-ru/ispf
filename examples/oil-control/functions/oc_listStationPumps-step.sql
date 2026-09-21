SELECT pump_no, product_code, status, total_l, shift_l, rate_lpm, error_pct, last_at::text AS last_at FROM oc_station_pump WHERE azs_code = COALESCE(NULLIF(?, ''), '005') ORDER BY pump_no
