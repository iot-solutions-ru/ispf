
SELECT o.job_order_id AS id, o.job_no, COALESCE(s.external_ref, '') AS external_ref,
       COALESCE(o.command, '') AS command, o.dispatch_status, o.equipment_id,
       COALESCE(r.product_definition_id, '') AS product_definition_id, r.quantity, COALESCE(r.uom, '') AS uom,
       o.planned_start, o.planned_end, o.created_at
FROM emc_job_order o
JOIN emc_work_request r ON r.request_id = o.request_id
JOIN emc_work_schedule s ON s.schedule_id = r.schedule_id
WHERE o.dispatch_status NOT IN ('ENDED', 'ABORTED', 'CANCELLED')
ORDER BY o.planned_start ASC, o.job_no
