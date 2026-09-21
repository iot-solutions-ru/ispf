SELECT shift_id, shift_label, planned_minutes FROM emc_work_calendar WHERE equipment_id = ? AND state = 'OPEN' ORDER BY planned_start DESC LIMIT 1
