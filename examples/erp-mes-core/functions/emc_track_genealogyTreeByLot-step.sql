
WITH RECURSIVE upstream (lot_id, linked_from_lot_id, quantity, definition_id, created_at, depth, path) AS (
  SELECT g.input_lot_id, g.output_lot_id, g.quantity, COALESCE(l.definition_id, ''), g.created_at, 1,
         CONCAT(g.output_lot_id, '>', g.input_lot_id)
  FROM emc_lot_genealogy g
  LEFT JOIN emc_material_lot l ON l.lot_id = g.input_lot_id
  WHERE g.output_lot_id = ?
  UNION ALL
  SELECT g.input_lot_id, g.output_lot_id, g.quantity, COALESCE(l.definition_id, ''), g.created_at,
         u.depth + 1, CONCAT(u.path, '>', g.input_lot_id)
  FROM upstream u
  JOIN emc_lot_genealogy g ON g.output_lot_id = u.lot_id
  LEFT JOIN emc_material_lot l ON l.lot_id = g.input_lot_id
  WHERE u.depth < 15
),
downstream (lot_id, linked_from_lot_id, quantity, definition_id, created_at, depth, path) AS (
  SELECT g.output_lot_id, g.input_lot_id, g.quantity, COALESCE(l.definition_id, ''), g.created_at, 1,
         CONCAT(g.input_lot_id, '>', g.output_lot_id)
  FROM emc_lot_genealogy g
  LEFT JOIN emc_material_lot l ON l.lot_id = g.output_lot_id
  WHERE g.input_lot_id = ?
  UNION ALL
  SELECT g.output_lot_id, g.input_lot_id, g.quantity, COALESCE(l.definition_id, ''), g.created_at,
         d.depth + 1, CONCAT(d.path, '>', g.output_lot_id)
  FROM downstream d
  JOIN emc_lot_genealogy g ON g.input_lot_id = d.lot_id
  LEFT JOIN emc_material_lot l ON l.lot_id = g.output_lot_id
  WHERE d.depth < 15
),
tree (direction, lot_id, linked_from_lot_id, quantity, definition_id, depth, path, created_at) AS (
  SELECT 'UPSTREAM', lot_id, linked_from_lot_id, quantity, definition_id, depth, path, created_at
  FROM upstream
  UNION ALL
  SELECT 'DOWNSTREAM', lot_id, linked_from_lot_id, quantity, definition_id, depth, path, created_at
  FROM downstream
)
SELECT direction, lot_id, linked_from_lot_id, quantity, definition_id, depth, path, created_at
FROM tree
WHERE (UPPER(COALESCE(NULLIF(TRIM(?), ''), 'BOTH')) = 'BOTH'
       OR UPPER(TRIM(?)) = tree.direction)
ORDER BY direction, depth, lot_id
