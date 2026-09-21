
SELECT g.input_lot_id, COALESCE(li.definition_id, '') AS input_material,
       g.output_lot_id, COALESCE(lo.definition_id, '') AS output_material,
       g.quantity, g.created_at
FROM emc_lot_genealogy g
LEFT JOIN emc_material_lot li ON li.lot_id = g.input_lot_id
LEFT JOIN emc_material_lot lo ON lo.lot_id = g.output_lot_id
WHERE ? <> '' AND (g.input_lot_id = ? OR g.output_lot_id = ?)
ORDER BY g.created_at, g.input_lot_id, g.output_lot_id
