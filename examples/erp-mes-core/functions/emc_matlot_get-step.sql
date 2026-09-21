SELECT lot_id, definition_id, status, COALESCE(storage_location, '') AS storage_location, quantity, base_uom FROM emc_material_lot WHERE barcode = ?
