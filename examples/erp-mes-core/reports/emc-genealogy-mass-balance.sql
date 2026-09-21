
SELECT COALESCE(a.definition_id, '') AS material_id, a.material_use,
       SUM(a.quantity) AS qty, COALESCE(MAX(a.uom), '') AS uom,
       COUNT(*) AS rows_n
FROM emc_material_actual a
GROUP BY a.definition_id, a.material_use
ORDER BY a.definition_id, a.material_use
