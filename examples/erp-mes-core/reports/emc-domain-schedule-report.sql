
SELECT schedule_id, domain, schedule_kind, COALESCE(target_id, '') AS target_id,
       quantity, COALESCE(uom, '') AS uom, status, COALESCE(note, '') AS note
FROM emc_domain_schedule
ORDER BY CASE domain
  WHEN 'PRODUCTION' THEN 1 WHEN 'QUALITY' THEN 2 WHEN 'INVENTORY' THEN 3 ELSE 4 END,
  schedule_id
