
WITH RECURSIVE downstream (root_lot, lot_id, linked_from_lot_id, quantity, definition_id, depth, path) AS (
  SELECT g.input_lot_id, g.output_lot_id, g.input_lot_id, g.quantity, COALESCE(l.definition_id, ''), 1,
         CONCAT(g.input_lot_id, '>', g.output_lot_id)
  FROM emc_lot_genealogy g
  LEFT JOIN emc_material_lot l ON l.lot_id = g.output_lot_id
  WHERE ? <> '' AND g.input_lot_id = ?
  UNION ALL
  SELECT d.root_lot, g.output_lot_id, g.input_lot_id, g.quantity, COALESCE(l.definition_id, ''),
         d.depth + 1, CONCAT(d.path, '>', g.output_lot_id)
  FROM downstream d
  JOIN emc_lot_genealogy g ON g.input_lot_id = d.lot_id
  LEFT JOIN emc_material_lot l ON l.lot_id = g.output_lot_id
  WHERE d.depth < 15
)
SELECT root_lot, depth, lot_id, definition_id AS material_id, linked_from_lot_id, quantity, path
FROM downstream
ORDER BY depth, lot_id
