SELECT COUNT(*)::int AS points FROM oc_calibration_row WHERE calibration_id = CAST(? AS uuid)
