SELECT shift_label, availability_pct, performance_pct, quality_pct, oee_pct FROM emc_oee_shift WHERE equipment_id = ? ORDER BY calculated_at DESC LIMIT 1
