SELECT id, downtime_minutes, planned_minutes FROM mes_oee_shift WHERE id = CAST(? AS uuid)
