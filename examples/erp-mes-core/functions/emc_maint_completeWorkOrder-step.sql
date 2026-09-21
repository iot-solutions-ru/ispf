UPDATE emc_maintenance_request SET status = 'CLOSED' WHERE request_id = (SELECT request_id FROM emc_maintenance_work_order WHERE wo_id = ?)
