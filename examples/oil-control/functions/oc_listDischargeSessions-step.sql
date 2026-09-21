SELECT id::text AS id, azs_code, ttn_no, started_at::text AS started_at, status, COALESCE(note,'') AS note FROM oc_discharge_session ORDER BY started_at DESC
