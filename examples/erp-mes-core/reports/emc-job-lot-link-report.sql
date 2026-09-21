
SELECT j.job_no, j.lot_id, j.link_role,
       COALESCE(l.definition_id, '') AS material_id, COALESCE(l.status, '') AS lot_status,
       COALESCE(o.dispatch_status, '') AS dispatch_status
FROM emc_job_lot_link j
LEFT JOIN emc_material_lot l ON l.lot_id = j.lot_id
LEFT JOIN emc_job_order o ON o.job_no = j.job_no
WHERE j.link_role = 'PRODUCED' AND j.lot_id LIKE 'LOT-FG-%'
ORDER BY j.job_no, j.lot_id
