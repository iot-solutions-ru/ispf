
WITH RECURSIVE upstream (root_lot, lot_id, linked_from_lot_id, quantity, definition_id, depth, path) AS (
  SELECT g.output_lot_id, g.input_lot_id, g.output_lot_id, g.quantity, COALESCE(l.definition_id, ''), 1,
         CONCAT(g.output_lot_id, '>', g.input_lot_id)
  FROM emc_lot_genealogy g
  LEFT JOIN emc_material_lot l ON l.lot_id = g.input_lot_id
  WHERE ? <> '' AND g.output_lot_id = ?
  UNION ALL
  SELECT u.root_lot, g.input_lot_id, g.output_lot_id, g.quantity, COALESCE(l.definition_id, ''),
         u.depth + 1, CONCAT(u.path, '>', g.input_lot_id)
  FROM upstream u
  JOIN emc_lot_genealogy g ON g.output_lot_id = u.lot_id
  LEFT JOIN emc_material_lot l ON l.lot_id = g.input_lot_id
  WHERE u.depth < 15
)
SELECT root_lot, depth, lot_id, definition_id AS material_id, linked_from_lot_id, quantity, path
FROM upstream
ORDER BY depth, lot_id
