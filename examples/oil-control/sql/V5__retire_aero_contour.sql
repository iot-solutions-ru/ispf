-- V5: убрать авиа/ТЗК-контур из активного демо, оставить только АЗС

-- деактивировать legacy резервуары (прирельс / расходный / перрон)
UPDATE oc_tank
SET active = false
WHERE site_code IN ('rail', 'expense', 'apron')
   OR tank_code IN ('RVS-1','RVS-2','RVS-3','RVS-4','RVS-5','RGS-1A','RGS-2A');

-- пометить legacy-площадки
UPDATE oc_site
SET display_name = CASE
  WHEN display_name LIKE '%(архив)%' THEN display_name
  ELSE display_name || ' (архив)'
END
WHERE site_code IN ('rail', 'expense', 'apron');

-- закрыть legacy-аномалии/инциденты ТЗК (не удаляем историю)
UPDATE oc_anomaly
SET status = 'closed', closed_at = COALESCE(closed_at, NOW())
WHERE status = 'open'
  AND (zone_code LIKE 'z1_%' OR zone_code LIKE 'z2_%' OR zone_code LIKE 'z3_%'
       OR zone_code LIKE 'z4_%' OR zone_code LIKE 'z5_%'
       OR title ILIKE '%ТЗА%' OR title ILIKE '%ТЗК%' OR title ILIKE '%РВС-ТЗА%');

UPDATE oc_incident
SET status = 'closed', closed_at = COALESCE(closed_at, NOW())
WHERE status IN ('new', 'assigned', 'open')
  AND (module = 'tzk' OR title ILIKE '%ТЗА%' OR title ILIKE '%ТЗК%');

-- партнёр авиа → не активен для B2B списков (оставляем запись)
UPDATE oc_b2b_order
SET status = 'cancelled'
WHERE partner_code = 'AIRLINE-SU' AND status NOT IN ('cancelled', 'closed');

-- ТЗА/ЖД транспорт в архивный статус
UPDATE oc_vehicle
SET status = 'archived'
WHERE vehicle_type IN ('tza', 'rail_tank')
   OR vehicle_code IN ('TZA-01', 'ZHD-8841');
