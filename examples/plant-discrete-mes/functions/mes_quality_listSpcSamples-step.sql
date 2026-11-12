SELECT id::text AS sample_id, line_code, measurement_mm, sample_ts::text AS sample_ts FROM mes_spc_sample ORDER BY sample_ts DESC LIMIT 20
