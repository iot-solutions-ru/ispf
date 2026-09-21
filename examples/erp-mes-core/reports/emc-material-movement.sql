
SELECT a.recorded_at, r.job_no, a.material_use, COALESCE(a.lot_id, '') AS lot_id,
       COALESCE(a.definition_id, '') AS material_id, a.quantity, COALESCE(a.uom, '') AS uom
FROM emc_material_actual a
JOIN emc_job_response r ON r.response_id = a.response_id
ORDER BY a.recorded_at DESC
