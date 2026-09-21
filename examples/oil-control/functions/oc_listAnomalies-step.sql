SELECT id::text AS id, anomaly_type, severity, title, zone_code, tank_code, status, detected_at::text AS detected_at FROM oc_anomaly ORDER BY detected_at DESC
