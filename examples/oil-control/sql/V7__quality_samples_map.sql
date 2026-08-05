-- V7: акты отбора проб + разведение пинов АЗС на карте

ALTER TABLE oc_lab_sample ADD COLUMN IF NOT EXISTS act_no VARCHAR(32);
ALTER TABLE oc_lab_sample ADD COLUMN IF NOT EXISTS azs_code VARCHAR(8);
ALTER TABLE oc_lab_sample ADD COLUMN IF NOT EXISTS product_code VARCHAR(32);
ALTER TABLE oc_lab_sample ADD COLUMN IF NOT EXISTS volume_ml DOUBLE PRECISION;
ALTER TABLE oc_lab_sample ADD COLUMN IF NOT EXISTS seal_no VARCHAR(64);
ALTER TABLE oc_lab_sample ADD COLUMN IF NOT EXISTS note TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS oc_lab_sample_act_no_uq ON oc_lab_sample (act_no) WHERE act_no IS NOT NULL;

-- развести станции на демо-карте (не накладывать подписи)
UPDATE oc_station SET map_x = 52, map_y = 34 WHERE azs_code = '014';
UPDATE oc_station SET map_x = 26, map_y = 40 WHERE azs_code = '005';
UPDATE oc_station SET map_x = 18, map_y = 78 WHERE azs_code = '870';
UPDATE oc_station SET map_x = 46, map_y = 18 WHERE azs_code = '231';
UPDATE oc_station SET map_x = 78, map_y = 46 WHERE azs_code = '842';
UPDATE oc_station SET map_x = 70, map_y = 32 WHERE azs_code = '374';
UPDATE oc_station SET map_x = 60, map_y = 52 WHERE azs_code = '243';
UPDATE oc_station SET map_x = 40, map_y = 58 WHERE azs_code = '103';
UPDATE oc_station SET map_x = 32, map_y = 66 WHERE azs_code = '040';
UPDATE oc_station SET map_x = 56, map_y = 70 WHERE azs_code = '015';

UPDATE oc_monitor_event SET map_x = 78, map_y = 46 WHERE azs_code = '842';
UPDATE oc_monitor_event SET map_x = 70, map_y = 32 WHERE azs_code = '374';
UPDATE oc_monitor_event SET map_x = 52, map_y = 34 WHERE azs_code = '014';
UPDATE oc_monitor_event SET map_x = 18, map_y = 78 WHERE azs_code = '870';
UPDATE oc_monitor_event SET map_x = 46, map_y = 18 WHERE azs_code = '231';
UPDATE oc_monitor_event SET map_x = 26, map_y = 40 WHERE azs_code = '005';

-- партии АЗС (если ещё нет)
INSERT INTO oc_batch (batch_id, product_code, origin, volume_l, status, quality_status)
SELECT 'AI92-2026-210', 'АИ-92', 'НБ Екатеринбург', 42000, 'active', 'pending'
WHERE NOT EXISTS (SELECT 1 FROM oc_batch WHERE batch_id='AI92-2026-210');
INSERT INTO oc_batch (batch_id, product_code, origin, volume_l, status, quality_status)
SELECT 'AI95-2026-118', 'АИ-95', 'НБ Омск', 28000, 'active', 'ok'
WHERE NOT EXISTS (SELECT 1 FROM oc_batch WHERE batch_id='AI95-2026-118');
INSERT INTO oc_batch (batch_id, product_code, origin, volume_l, status, quality_status)
SELECT 'DT-2026-044', 'ДТ', 'НБ Новосибирск', 36000, 'active', 'in_lab'
WHERE NOT EXISTS (SELECT 1 FROM oc_batch WHERE batch_id='DT-2026-044');

INSERT INTO oc_quality_passport (id, batch_id, density_kg_m3, flash_point_c, freezing_point_c, water_ppm, particulate_mg_l, lab_name, tested_at, conclusion, certificate_no)
SELECT 'c4000000-0000-4000-8000-000000000001', 'AI95-2026-118', 748.4, 28, NULL, 22, 0.3, 'Лаб. качества · Екб', NOW() - INTERVAL '6 hours', 'pass', 'ПК-АЗС-118/26'
WHERE NOT EXISTS (SELECT 1 FROM oc_quality_passport WHERE id='c4000000-0000-4000-8000-000000000001');

-- акты отбора проб
INSERT INTO oc_lab_sample (id, act_no, batch_id, azs_code, tank_code, product_code, sample_point, sampled_at, operator_name, status, volume_ml, seal_no, note)
SELECT 'c5000000-0000-4000-8000-000000000001', 'АОП-2026-014-01', 'AI92-2026-210', '014', 'AZS-014-1', 'АИ-92',
       'РГС-1 · верхний слой', NOW() - INTERVAL '3 hours', 'Козлов М.', 'in_lab', 1000, 'PL-014-8821',
       'Отбор при приёме с АЦ'
WHERE NOT EXISTS (SELECT 1 FROM oc_lab_sample WHERE id='c5000000-0000-4000-8000-000000000001');

INSERT INTO oc_lab_sample (id, act_no, batch_id, azs_code, tank_code, product_code, sample_point, sampled_at, operator_name, status, volume_ml, seal_no, note)
SELECT 'c5000000-0000-4000-8000-000000000002', 'АОП-2026-005-03', 'AI95-2026-118', '005', 'AZS-005-2', 'АИ-95',
       'РГС-2 · средний слой', NOW() - INTERVAL '1 day', 'Смирнова Е.', 'tested', 1000, 'PL-005-4410',
       'Плановый контроль'
WHERE NOT EXISTS (SELECT 1 FROM oc_lab_sample WHERE id='c5000000-0000-4000-8000-000000000002');

INSERT INTO oc_lab_sample (id, act_no, batch_id, azs_code, tank_code, product_code, sample_point, sampled_at, operator_name, status, volume_ml, seal_no, note)
SELECT 'c5000000-0000-4000-8000-000000000003', 'АОП-2026-870-02', 'DT-2026-044', '870', 'AZS-870-3', 'ДТ',
       'РГС-3 · донный слой', NOW() - INTERVAL '40 minutes', 'Иванов А.', 'sampled', 500, 'PL-870-2201',
       'Контроль после слива'
WHERE NOT EXISTS (SELECT 1 FROM oc_lab_sample WHERE id='c5000000-0000-4000-8000-000000000003');

-- дополнить старую пробу номером акта
UPDATE oc_lab_sample
SET act_no = COALESCE(act_no, 'АОП-LEGACY-001'),
    product_code = COALESCE(product_code, 'ТС-1'),
    volume_ml = COALESCE(volume_ml, 1000)
WHERE id = '55555555-5555-5555-5555-555555555501';
