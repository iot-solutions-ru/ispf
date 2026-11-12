UPDATE mes_oee_shift SET downtime_minutes = LEAST(planned_minutes, downtime_minutes + ?) WHERE id = CAST(? AS uuid)
