SELECT id::text AS id, tank_code, measured_at::text AS measured_at, level_cm, temperature_c, volume_l, operator_name FROM oc_measurement ORDER BY measured_at DESC LIMIT 100
