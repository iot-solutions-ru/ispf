
SELECT l.lot_id, l.barcode, l.definition_id AS material_id, COALESCE(d.class_id, '') AS class_id,
       l.quantity, l.base_uom AS uom, l.status, COALESCE(l.storage_location, '') AS storage_location
FROM emc_material_lot l JOIN emc_material_definition d ON d.definition_id = l.definition_id
ORDER BY l.lot_id
