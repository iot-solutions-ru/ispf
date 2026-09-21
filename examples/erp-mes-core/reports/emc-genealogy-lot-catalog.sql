
SELECT lot_id, barcode, definition_id AS material_id, status, storage_location, quantity, base_uom
FROM emc_material_lot
WHERE lot_id LIKE 'LOT-FG-%' OR lot_id LIKE 'LOT-WIP-%' OR lot_id LIKE 'LOT-RAW-%'
ORDER BY CASE WHEN lot_id LIKE 'LOT-FG-%' THEN 1 WHEN lot_id LIKE 'LOT-WIP-%' THEN 2 ELSE 3 END, lot_id
