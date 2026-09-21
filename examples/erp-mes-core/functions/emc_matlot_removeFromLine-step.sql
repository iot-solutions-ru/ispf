UPDATE emc_material_lot SET status = 'STOCK', storage_location = ?, on_equipment_id = NULL, on_job_order_id = NULL, version_no = version_no + 1 WHERE lot_id = ?
