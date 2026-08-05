-- V4: контур АЗС (регионы, станции, РГС–ТРК, мониторинг, KPI) под SPA «Ойл Контроль»

CREATE TABLE IF NOT EXISTS oc_region (
  region_code VARCHAR(16) PRIMARY KEY,
  display_name VARCHAR(128) NOT NULL,
  stations_total INTEGER NOT NULL DEFAULT 0,
  stations_connected INTEGER NOT NULL DEFAULT 0,
  sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS oc_station (
  azs_code VARCHAR(8) PRIMARY KEY,
  display_name VARCHAR(128) NOT NULL,
  region_code VARCHAR(16) REFERENCES oc_region(region_code),
  address VARCHAR(256),
  lat DOUBLE PRECISION,
  lon DOUBLE PRECISION,
  connected BOOLEAN NOT NULL DEFAULT true,
  map_x DOUBLE PRECISION,
  map_y DOUBLE PRECISION
);

ALTER TABLE oc_tank ADD COLUMN IF NOT EXISTS azs_code VARCHAR(8);
ALTER TABLE oc_tank ADD COLUMN IF NOT EXISTS tank_no INTEGER;
ALTER TABLE oc_tank ADD COLUMN IF NOT EXISTS capacity_l DOUBLE PRECISION;
ALTER TABLE oc_tank ALTER COLUMN product_code TYPE VARCHAR(32);
ALTER TABLE oc_batch ALTER COLUMN product_code TYPE VARCHAR(32);

CREATE TABLE IF NOT EXISTS oc_imbalance (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  azs_code VARCHAR(8) NOT NULL,
  tank_no INTEGER NOT NULL,
  product_code VARCHAR(16) NOT NULL,
  period_label VARCHAR(64),
  shifts_label VARCHAR(64),
  detected_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  delta_l DOUBLE PRECISION NOT NULL,
  delta_kg DOUBLE PRECISION,
  cause TEXT,
  status VARCHAR(32) NOT NULL DEFAULT 'open'
);

CREATE TABLE IF NOT EXISTS oc_monitor_event (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  severity VARCHAR(16) NOT NULL DEFAULT 'info',
  started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  ended_at TIMESTAMPTZ,
  azs_code VARCHAR(8),
  address VARCHAR(256),
  event_text TEXT NOT NULL,
  source_name VARCHAR(64),
  map_x DOUBLE PRECISION,
  map_y DOUBLE PRECISION,
  status VARCHAR(32) NOT NULL DEFAULT 'open'
);

CREATE TABLE IF NOT EXISTS oc_rgs_trk (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  azs_code VARCHAR(8) NOT NULL,
  site_type VARCHAR(16) NOT NULL DEFAULT 'АЗС',
  tank_no INTEGER NOT NULL,
  product_code VARCHAR(16) NOT NULL,
  detected_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  rgs_l DOUBLE PRECISION NOT NULL,
  trk_l DOUBLE PRECISION NOT NULL,
  abs_dev_l DOUBLE PRECISION NOT NULL,
  rel_dev_pct DOUBLE PRECISION NOT NULL
);

CREATE TABLE IF NOT EXISTS oc_kpi_report (
  report_id VARCHAR(32) PRIMARY KEY,
  title VARCHAR(256) NOT NULL,
  report_group VARCHAR(128) NOT NULL,
  period_label VARCHAR(64),
  format VARCHAR(16) NOT NULL DEFAULT 'Excel',
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  row_count INTEGER NOT NULL DEFAULT 0,
  owner_name VARCHAR(128),
  status VARCHAR(32) NOT NULL DEFAULT 'готов',
  summary TEXT
);

CREATE TABLE IF NOT EXISTS oc_wagon_trip (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  wagon_no VARCHAR(32) NOT NULL,
  product_code VARCHAR(16) NOT NULL,
  from_station VARCHAR(128),
  to_station VARCHAR(128),
  waybill VARCHAR(64),
  shipped_at TIMESTAMPTZ,
  current_station VARCHAR(128),
  operation VARCHAR(64),
  op_at TIMESTAMPTZ,
  km_left INTEGER,
  delay_bucket VARCHAR(16) NOT NULL DEFAULT 'lt1'
);

CREATE TABLE IF NOT EXISTS oc_tank_stock (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  azs_code VARCHAR(8) NOT NULL,
  address VARCHAR(256),
  tank_no INTEGER NOT NULL,
  product_code VARCHAR(16) NOT NULL,
  density_kg_m3 DOUBLE PRECISION,
  stock_l DOUBLE PRECISION NOT NULL,
  fill_pct DOUBLE PRECISION,
  free_l DOUBLE PRECISION,
  dead_l DOUBLE PRECISION,
  measured_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- === регионы ===
INSERT INTO oc_region (region_code, display_name, stations_total, stations_connected, sort_order)
SELECT 'ekb', 'Екатеринбург', 84, 81, 1 WHERE NOT EXISTS (SELECT 1 FROM oc_region WHERE region_code='ekb');
INSERT INTO oc_region (region_code, display_name, stations_total, stations_connected, sort_order)
SELECT 'kem', 'Кемерово', 42, 40, 2 WHERE NOT EXISTS (SELECT 1 FROM oc_region WHERE region_code='kem');
INSERT INTO oc_region (region_code, display_name, stations_total, stations_connected, sort_order)
SELECT 'krd', 'Краснодар', 96, 93, 3 WHERE NOT EXISTS (SELECT 1 FROM oc_region WHERE region_code='krd');
INSERT INTO oc_region (region_code, display_name, stations_total, stations_connected, sort_order)
SELECT 'krs', 'Красноярск', 51, 49, 4 WHERE NOT EXISTS (SELECT 1 FROM oc_region WHERE region_code='krs');
INSERT INTO oc_region (region_code, display_name, stations_total, stations_connected, sort_order)
SELECT 'tyu', 'Тюмень', 38, 38, 5 WHERE NOT EXISTS (SELECT 1 FROM oc_region WHERE region_code='tyu');
INSERT INTO oc_region (region_code, display_name, stations_total, stations_connected, sort_order)
SELECT 'msk', 'Москва', 120, 118, 6 WHERE NOT EXISTS (SELECT 1 FROM oc_region WHERE region_code='msk');

-- === АЗС (3-значные коды) ===
INSERT INTO oc_station (azs_code, display_name, region_code, address, lat, lon, map_x, map_y)
SELECT '014', 'АЗС 014', 'ekb', 'Екатеринбург, ул. Репина 12', 56.838, 60.597, 52, 34
WHERE NOT EXISTS (SELECT 1 FROM oc_station WHERE azs_code='014');
INSERT INTO oc_station (azs_code, display_name, region_code, address, lat, lon, map_x, map_y)
SELECT '005', 'АЗС 005', 'msk', 'Москва, МКАД 47 км', 55.755, 37.617, 26, 40
WHERE NOT EXISTS (SELECT 1 FROM oc_station WHERE azs_code='005');
INSERT INTO oc_station (azs_code, display_name, region_code, address, lat, lon, map_x, map_y)
SELECT '870', 'АЗС 870', 'krd', 'Краснодар, ул. Красная 88', 45.035, 38.975, 18, 78
WHERE NOT EXISTS (SELECT 1 FROM oc_station WHERE azs_code='870');
INSERT INTO oc_station (azs_code, display_name, region_code, address, lat, lon, map_x, map_y)
SELECT '231', 'АЗС 231', 'tyu', 'Тюмень, ул. Республики 5', 57.153, 65.534, 46, 18
WHERE NOT EXISTS (SELECT 1 FROM oc_station WHERE azs_code='231');
INSERT INTO oc_station (azs_code, display_name, region_code, address, lat, lon, map_x, map_y)
SELECT '842', 'АЗС 842', 'kem', 'Кемеровская обл., Ленинск-Кузнецкий', 54.663, 86.162, 78, 46
WHERE NOT EXISTS (SELECT 1 FROM oc_station WHERE azs_code='842');
INSERT INTO oc_station (azs_code, display_name, region_code, address, lat, lon, map_x, map_y)
SELECT '374', 'АЗС 374', 'krs', 'Новосибирская обл., Бердск', 54.758, 83.096, 70, 32
WHERE NOT EXISTS (SELECT 1 FROM oc_station WHERE azs_code='374');
INSERT INTO oc_station (azs_code, display_name, region_code, address, lat, lon, map_x, map_y)
SELECT '243', 'АЗС 243', 'ekb', 'Екатеринбург, Сибирский тракт', 56.810, 60.650, 60, 52
WHERE NOT EXISTS (SELECT 1 FROM oc_station WHERE azs_code='243');
INSERT INTO oc_station (azs_code, display_name, region_code, address, lat, lon, map_x, map_y)
SELECT '103', 'АЗС 103', 'ekb', 'Екатеринбург, ВИЗ', 56.845, 60.570, 40, 58
WHERE NOT EXISTS (SELECT 1 FROM oc_station WHERE azs_code='103');
INSERT INTO oc_station (azs_code, display_name, region_code, address, lat, lon, map_x, map_y)
SELECT '040', 'АЗС 040', 'msk', 'Московская обл., Химки', 55.889, 37.430, 32, 66
WHERE NOT EXISTS (SELECT 1 FROM oc_station WHERE azs_code='040');
INSERT INTO oc_station (azs_code, display_name, region_code, address, lat, lon, map_x, map_y)
SELECT '015', 'АЗС 015', 'ekb', 'Екатеринбург, ул. Малышева', 56.835, 60.612, 56, 70
WHERE NOT EXISTS (SELECT 1 FROM oc_station WHERE azs_code='015');

-- site-записи для АЗС (совместимость с oc_tank.site_code)
INSERT INTO oc_site (site_code, display_name, site_type)
SELECT 'azs-' || azs_code, display_name, 'azs' FROM oc_station s
WHERE NOT EXISTS (SELECT 1 FROM oc_site WHERE site_code = 'azs-' || s.azs_code);

-- зоны баланса АЗС
INSERT INTO oc_site (site_code, display_name, site_type)
SELECT 'azs-net', 'Сеть АЗС', 'network'
WHERE NOT EXISTS (SELECT 1 FROM oc_site WHERE site_code='azs-net');

INSERT INTO oc_zone (zone_code, display_name, site_code, inflow_desc, outflow_desc, calc_period, loss_norm_pct, sort_order)
SELECT 'z_nb_rgs', 'НБ → РГС (приём на АЗС)', 'azs-net', 'ТТН / вторичная логистика', 'Остатки РГС', 'shift', 0.25, 10
WHERE NOT EXISTS (SELECT 1 FROM oc_zone WHERE zone_code='z_nb_rgs');
INSERT INTO oc_zone (zone_code, display_name, site_code, inflow_desc, outflow_desc, calc_period, loss_norm_pct, sort_order)
SELECT 'z_rgs_trk', 'РГС → ТРК (реализация)', 'azs-net', 'Остатки РГС', 'Отпуск через ТРК / СУ АЗС', 'shift', 0.25, 11
WHERE NOT EXISTS (SELECT 1 FROM oc_zone WHERE zone_code='z_rgs_trk');

-- РГС на демо-АЗС
INSERT INTO oc_tank (tank_code, display_name, site_code, tank_kind, max_level_cm, product_code, active, azs_code, tank_no, capacity_l)
SELECT 'AZS-014-1', 'РГС-1 · АЗС 014', 'azs-014', 'RGS', 400, 'АИ-92', true, '014', 1, 28800
WHERE NOT EXISTS (SELECT 1 FROM oc_tank WHERE tank_code='AZS-014-1');
INSERT INTO oc_tank (tank_code, display_name, site_code, tank_kind, max_level_cm, product_code, active, azs_code, tank_no, capacity_l)
SELECT 'AZS-014-2', 'РГС-2 · АЗС 014', 'azs-014', 'RGS', 400, 'АИ-95', true, '014', 2, 31100
WHERE NOT EXISTS (SELECT 1 FROM oc_tank WHERE tank_code='AZS-014-2');
INSERT INTO oc_tank (tank_code, display_name, site_code, tank_kind, max_level_cm, product_code, active, azs_code, tank_no, capacity_l)
SELECT 'AZS-005-3', 'РГС-3 · АЗС 005', 'azs-005', 'RGS', 400, 'ДТ', true, '005', 3, 28000
WHERE NOT EXISTS (SELECT 1 FROM oc_tank WHERE tank_code='AZS-005-3');
INSERT INTO oc_tank (tank_code, display_name, site_code, tank_kind, max_level_cm, product_code, active, azs_code, tank_no, capacity_l)
SELECT 'AZS-005-1', 'РГС-1 · АЗС 005', 'azs-005', 'RGS', 400, 'АИ-92', true, '005', 1, 25000
WHERE NOT EXISTS (SELECT 1 FROM oc_tank WHERE tank_code='AZS-005-1');
INSERT INTO oc_tank (tank_code, display_name, site_code, tank_kind, max_level_cm, product_code, active, azs_code, tank_no, capacity_l)
SELECT 'AZS-870-1', 'РГС-1 · АЗС 870', 'azs-870', 'RGS', 400, 'АИ-92', true, '870', 1, 24000
WHERE NOT EXISTS (SELECT 1 FROM oc_tank WHERE tank_code='AZS-870-1');
INSERT INTO oc_tank (tank_code, display_name, site_code, tank_kind, max_level_cm, product_code, active, azs_code, tank_no, capacity_l)
SELECT 'AZS-231-4', 'РГС-4 · АЗС 231', 'azs-231', 'RGS', 350, 'СУГ', true, '231', 4, 11000
WHERE NOT EXISTS (SELECT 1 FROM oc_tank WHERE tank_code='AZS-231-4');
INSERT INTO oc_tank (tank_code, display_name, site_code, tank_kind, max_level_cm, product_code, active, azs_code, tank_no, capacity_l)
SELECT 'AZS-243-2', 'РГС-2 · АЗС 243', 'azs-243', 'RGS', 400, 'АИ-95', true, '243', 2, 22000
WHERE NOT EXISTS (SELECT 1 FROM oc_tank WHERE tank_code='AZS-243-2');
INSERT INTO oc_tank (tank_code, display_name, site_code, tank_kind, max_level_cm, product_code, active, azs_code, tank_no, capacity_l)
SELECT 'AZS-103-5', 'РГС-5 · АЗС 103', 'azs-103', 'RGS', 400, 'ДТ', true, '103', 5, 30000
WHERE NOT EXISTS (SELECT 1 FROM oc_tank WHERE tank_code='AZS-103-5');
INSERT INTO oc_tank (tank_code, display_name, site_code, tank_kind, max_level_cm, product_code, active, azs_code, tank_no, capacity_l)
SELECT 'AZS-040-4', 'РГС-4 · АЗС 040', 'azs-040', 'RGS', 400, 'АИ-92', true, '040', 4, 26000
WHERE NOT EXISTS (SELECT 1 FROM oc_tank WHERE tank_code='AZS-040-4');

-- градуировки для новых РГС
INSERT INTO oc_calibration (id, tank_code, version_no, valid_from, valid_to, status, source_name)
SELECT v.id, v.tank_code, 1, CURRENT_DATE - 30, CURRENT_DATE + 300, 'active', 'seed-azs-linear'
FROM (VALUES
  ('f0000000-0000-4000-8000-000000000001'::uuid, 'AZS-014-1'),
  ('f0000000-0000-4000-8000-000000000002'::uuid, 'AZS-014-2'),
  ('f0000000-0000-4000-8000-000000000003'::uuid, 'AZS-005-3'),
  ('f0000000-0000-4000-8000-000000000004'::uuid, 'AZS-005-1'),
  ('f0000000-0000-4000-8000-000000000005'::uuid, 'AZS-870-1'),
  ('f0000000-0000-4000-8000-000000000006'::uuid, 'AZS-231-4'),
  ('f0000000-0000-4000-8000-000000000007'::uuid, 'AZS-243-2'),
  ('f0000000-0000-4000-8000-000000000008'::uuid, 'AZS-103-5'),
  ('f0000000-0000-4000-8000-000000000009'::uuid, 'AZS-040-4')
) AS v(id, tank_code)
WHERE NOT EXISTS (SELECT 1 FROM oc_calibration c WHERE c.tank_code = v.tank_code);

INSERT INTO oc_calibration_row (calibration_id, level_cm, volume_l)
SELECT c.id, v.level_cm, v.volume_l * COALESCE(t.capacity_l, 8000) / 8000.0
FROM oc_calibration c
JOIN oc_tank t ON t.tank_code = c.tank_code
JOIN (VALUES (0::float,0::float),(200::float,4000::float),(400::float,8000::float)) AS v(level_cm, volume_l) ON true
WHERE c.tank_code LIKE 'AZS-%'
  AND NOT EXISTS (SELECT 1 FROM oc_calibration_row r WHERE r.calibration_id=c.id AND r.level_cm=v.level_cm);

-- замеры
INSERT INTO oc_measurement (id, tank_code, measured_at, level_cm, temperature_c, volume_l, operator_name, note)
SELECT 'a1000000-0000-4000-8000-000000000001', 'AZS-014-1', NOW() - INTERVAL '1 hour', 256, 12.4, 18420, 'demo', 'seed-azs'
WHERE NOT EXISTS (SELECT 1 FROM oc_measurement WHERE id='a1000000-0000-4000-8000-000000000001');
INSERT INTO oc_measurement (id, tank_code, measured_at, level_cm, temperature_c, volume_l, operator_name, note)
SELECT 'a1000000-0000-4000-8000-000000000002', 'AZS-014-2', NOW() - INTERVAL '1 hour', 284, 11.8, 22104, 'demo', 'seed-azs'
WHERE NOT EXISTS (SELECT 1 FROM oc_measurement WHERE id='a1000000-0000-4000-8000-000000000002');
INSERT INTO oc_measurement (id, tank_code, measured_at, level_cm, temperature_c, volume_l, operator_name, note)
SELECT 'a1000000-0000-4000-8000-000000000003', 'AZS-005-3', NOW() - INTERVAL '2 hours', 232, 10.2, 16200, 'demo', 'seed-azs'
WHERE NOT EXISTS (SELECT 1 FROM oc_measurement WHERE id='a1000000-0000-4000-8000-000000000003');

-- дебалансы
INSERT INTO oc_imbalance (id, azs_code, tank_no, product_code, period_label, shifts_label, detected_at, delta_l, delta_kg, cause)
SELECT 'b1000000-0000-4000-8000-000000000001', '040', 4, 'АИ-92', '29.07–02.08.2026', '12, 13, 14',
       TIMESTAMPTZ '2026-08-03 23:53:00+03', -251, -188,
       'Инцидентов не зафиксировано, требуется доп. проверка работы СИ'
WHERE NOT EXISTS (SELECT 1 FROM oc_imbalance WHERE id='b1000000-0000-4000-8000-000000000001');
INSERT INTO oc_imbalance (id, azs_code, tank_no, product_code, period_label, shifts_label, detected_at, delta_l, delta_kg, cause)
SELECT 'b1000000-0000-4000-8000-000000000002', '015', 2, 'ДТ', '01–03.08.2026', '1, 2',
       TIMESTAMPTZ '2026-08-03 18:20:00+03', -88, -73,
       'Отклонение РГС–ТРК в смене 2'
WHERE NOT EXISTS (SELECT 1 FROM oc_imbalance WHERE id='b1000000-0000-4000-8000-000000000002');
INSERT INTO oc_imbalance (id, azs_code, tank_no, product_code, period_label, shifts_label, detected_at, delta_l, delta_kg, cause)
SELECT 'b1000000-0000-4000-8000-000000000003', '870', 1, 'АИ-95', '31.07–02.08.2026', '8, 9',
       TIMESTAMPTZ '2026-08-02 21:05:00+03', 142, 106,
       'Возможен недоввод приёмки по ТТН'
WHERE NOT EXISTS (SELECT 1 FROM oc_imbalance WHERE id='b1000000-0000-4000-8000-000000000003');

-- события мониторинга
INSERT INTO oc_monitor_event (id, severity, started_at, azs_code, address, event_text, source_name, map_x, map_y)
SELECT 'c1000000-0000-4000-8000-000000000001', 'warn', TIMESTAMPTZ '2026-08-03 19:09:22+03', '842',
       'Кемеровская обл., Ленинск-Кузнецкий', 'Нет движения топлива больше 30 минут · АИ-92', 'Уровнемер', 62, 48
WHERE NOT EXISTS (SELECT 1 FROM oc_monitor_event WHERE id='c1000000-0000-4000-8000-000000000001');
INSERT INTO oc_monitor_event (id, severity, started_at, azs_code, address, event_text, source_name, map_x, map_y)
SELECT 'c1000000-0000-4000-8000-000000000002', 'crit', TIMESTAMPTZ '2026-08-03 18:41:05+03', '374',
       'Новосибирская обл., Бердск', 'Низкое напряжение на входе 0В · ИБП', 'ИБП', 58, 42
WHERE NOT EXISTS (SELECT 1 FROM oc_monitor_event WHERE id='c1000000-0000-4000-8000-000000000002');
INSERT INTO oc_monitor_event (id, severity, started_at, azs_code, address, event_text, source_name, map_x, map_y)
SELECT 'c1000000-0000-4000-8000-000000000003', 'info', TIMESTAMPTZ '2026-08-03 18:12:40+03', '014',
       'Свердловская обл., Екатеринбург', 'Обнаружена поставка НП · ДТ', 'СУ АЗС', 48, 38
WHERE NOT EXISTS (SELECT 1 FROM oc_monitor_event WHERE id='c1000000-0000-4000-8000-000000000003');
INSERT INTO oc_monitor_event (id, severity, started_at, azs_code, address, event_text, source_name, map_x, map_y)
SELECT 'c1000000-0000-4000-8000-000000000004', 'warn', TIMESTAMPTZ '2026-08-03 17:55:11+03', '870',
       'Краснодарский край', 'Нет движения топлива больше 30 минут · АИ-95', 'Уровнемер', 28, 72
WHERE NOT EXISTS (SELECT 1 FROM oc_monitor_event WHERE id='c1000000-0000-4000-8000-000000000004');
INSERT INTO oc_monitor_event (id, severity, started_at, azs_code, address, event_text, source_name, map_x, map_y)
SELECT 'c1000000-0000-4000-8000-000000000005', 'crit', TIMESTAMPTZ '2026-08-03 16:48:30+03', '005',
       'Московская обл.', 'Погрешность ТРК выше допуска · колонка 3', 'ТРК', 35, 45
WHERE NOT EXISTS (SELECT 1 FROM oc_monitor_event WHERE id='c1000000-0000-4000-8000-000000000005');

-- РГС–ТРК
INSERT INTO oc_rgs_trk (id, azs_code, tank_no, product_code, detected_at, rgs_l, trk_l, abs_dev_l, rel_dev_pct)
SELECT 'd1000000-0000-4000-8000-000000000001', '243', 2, 'АИ-95', TIMESTAMPTZ '2026-08-02 07:08:10+03', 1840, 1792, 48, 2.6
WHERE NOT EXISTS (SELECT 1 FROM oc_rgs_trk WHERE id='d1000000-0000-4000-8000-000000000001');
INSERT INTO oc_rgs_trk (id, azs_code, tank_no, product_code, detected_at, rgs_l, trk_l, abs_dev_l, rel_dev_pct)
SELECT 'd1000000-0000-4000-8000-000000000002', '005', 1, 'АИ-92', TIMESTAMPTZ '2026-08-02 09:22:41+03', 2204, 2188, 16, 0.7
WHERE NOT EXISTS (SELECT 1 FROM oc_rgs_trk WHERE id='d1000000-0000-4000-8000-000000000002');
INSERT INTO oc_rgs_trk (id, azs_code, tank_no, product_code, detected_at, rgs_l, trk_l, abs_dev_l, rel_dev_pct)
SELECT 'd1000000-0000-4000-8000-000000000003', '014', 4, 'ДТ', TIMESTAMPTZ '2026-08-01 21:15:03+03', 3120, 2988, 132, 4.2
WHERE NOT EXISTS (SELECT 1 FROM oc_rgs_trk WHERE id='d1000000-0000-4000-8000-000000000003');
INSERT INTO oc_rgs_trk (id, azs_code, tank_no, product_code, detected_at, rgs_l, trk_l, abs_dev_l, rel_dev_pct)
SELECT 'd1000000-0000-4000-8000-000000000004', '870', 3, 'АИ-95', TIMESTAMPTZ '2026-08-01 14:40:55+03', 990, 1005, -15, -1.5
WHERE NOT EXISTS (SELECT 1 FROM oc_rgs_trk WHERE id='d1000000-0000-4000-8000-000000000004');

-- остатки
INSERT INTO oc_tank_stock (id, azs_code, address, tank_no, product_code, density_kg_m3, stock_l, fill_pct, free_l, dead_l)
SELECT 'e1000000-0000-4000-8000-000000000001', '014', 'Екатеринбург, ул. Репина 12', 1, 'АИ-92', 742.1, 18420, 64, 10380, 420
WHERE NOT EXISTS (SELECT 1 FROM oc_tank_stock WHERE id='e1000000-0000-4000-8000-000000000001');
INSERT INTO oc_tank_stock (id, azs_code, address, tank_no, product_code, density_kg_m3, stock_l, fill_pct, free_l, dead_l)
SELECT 'e1000000-0000-4000-8000-000000000002', '014', 'Екатеринбург, ул. Репина 12', 2, 'АИ-95', 748.4, 22104, 71, 8996, 410
WHERE NOT EXISTS (SELECT 1 FROM oc_tank_stock WHERE id='e1000000-0000-4000-8000-000000000002');
INSERT INTO oc_tank_stock (id, azs_code, address, tank_no, product_code, density_kg_m3, stock_l, fill_pct, free_l, dead_l)
SELECT 'e1000000-0000-4000-8000-000000000003', '005', 'Москва, МКАД 47 км', 3, 'ДТ', 835.2, 16200, 58, 11800, 520
WHERE NOT EXISTS (SELECT 1 FROM oc_tank_stock WHERE id='e1000000-0000-4000-8000-000000000003');
INSERT INTO oc_tank_stock (id, azs_code, address, tank_no, product_code, density_kg_m3, stock_l, fill_pct, free_l, dead_l)
SELECT 'e1000000-0000-4000-8000-000000000004', '870', 'Краснодар, ул. Красная 88', 1, 'АИ-92', 741.0, 9800, 41, 14200, 400
WHERE NOT EXISTS (SELECT 1 FROM oc_tank_stock WHERE id='e1000000-0000-4000-8000-000000000004');
INSERT INTO oc_tank_stock (id, azs_code, address, tank_no, product_code, density_kg_m3, stock_l, fill_pct, free_l, dead_l)
SELECT 'e1000000-0000-4000-8000-000000000005', '231', 'Тюмень, ул. Республики 5', 4, 'СУГ', 540.5, 4200, 39, 6800, 180
WHERE NOT EXISTS (SELECT 1 FROM oc_tank_stock WHERE id='e1000000-0000-4000-8000-000000000005');

-- KPI-отчёты
INSERT INTO oc_kpi_report (report_id, title, report_group, period_label, format, updated_at, row_count, owner_name, status, summary)
SELECT 'r-si', 'Отчёт по поверке СИ', 'Поверка СИ', 'Август 2026', 'Excel', TIMESTAMPTZ '2026-08-03 18:40:00+03', 11891, 'Метрология', 'готов',
       'Выполнено / осталось ≤30 дней / просрочено по регионам и типам СИ'
WHERE NOT EXISTS (SELECT 1 FROM oc_kpi_report WHERE report_id='r-si');
INSERT INTO oc_kpi_report (report_id, title, report_group, period_label, format, updated_at, row_count, owner_name, status, summary)
SELECT 'r-clean', 'План-факт зачистки резервуаров', 'Зачистка резервуаров', 'III квартал 2026', 'PDF', TIMESTAMPTZ '2026-08-02 11:15:00+03', 214, 'Эксплуатация', 'формируется',
       'График зачисток РГС, отклонения от плана, простои по АЗС'
WHERE NOT EXISTS (SELECT 1 FROM oc_kpi_report WHERE report_id='r-clean');
INSERT INTO oc_kpi_report (report_id, title, report_group, period_label, format, updated_at, row_count, owner_name, status, summary)
SELECT 'r-lab', 'Лабораторные испытания НП', 'Лабораторные испытания', 'Июль–август 2026', 'Excel', TIMESTAMPTZ '2026-08-03 09:22:00+03', 2692, 'Качество', 'готов',
       'Пробы, протоколы, несоответствия ГОСТ по маркам НП'
WHERE NOT EXISTS (SELECT 1 FROM oc_kpi_report WHERE report_id='r-lab');
INSERT INTO oc_kpi_report (report_id, title, report_group, period_label, format, updated_at, row_count, owner_name, status, summary)
SELECT 'r-iis', 'Доступность ИИС по АЗС', 'Доступность ИИС', 'Сутки / 7 дней', 'CSV', TIMESTAMPTZ '2026-08-03 19:05:00+03', 842, 'ИТ', 'готов',
       'В работе · ошибка · авария · нет связи с разбивкой по регионам'
WHERE NOT EXISTS (SELECT 1 FROM oc_kpi_report WHERE report_id='r-iis');
INSERT INTO oc_kpi_report (report_id, title, report_group, period_label, format, updated_at, row_count, owner_name, status, summary)
SELECT 'r-trk', 'Погрешность ТРК', 'Погрешность ТРК', 'Август 2026', 'Excel', TIMESTAMPTZ '2026-08-03 16:48:00+03', 5513, 'Метрология', 'готов',
       'Колонки с погрешностью <0,25% / 0,25–0,5% / >0,5%'
WHERE NOT EXISTS (SELECT 1 FROM oc_kpi_report WHERE report_id='r-trk');
INSERT INTO oc_kpi_report (report_id, title, report_group, period_label, format, updated_at, row_count, owner_name, status, summary)
SELECT 'r-wo', 'Исполнение заявок ТОиР', 'Заявки', 'Август 2026', 'PDF', TIMESTAMPTZ '2026-08-03 14:10:00+03', 22578, 'Диспетчеризация', 'готов',
       'Закрыто · открыто · с нарушением сроков (SLA)'
WHERE NOT EXISTS (SELECT 1 FROM oc_kpi_report WHERE report_id='r-wo');
INSERT INTO oc_kpi_report (report_id, title, report_group, period_label, format, updated_at, row_count, owner_name, status, summary)
SELECT 'r-short', 'Журнал недовозов', 'Инциденты', '01–03.08.2026', 'Excel', TIMESTAMPTZ '2026-08-03 17:30:00+03', 9, 'Логистика', 'готов',
       'Недовозы по ТТН: АЗС, масса, отклонение, статус разбора'
WHERE NOT EXISTS (SELECT 1 FROM oc_kpi_report WHERE report_id='r-short');
INSERT INTO oc_kpi_report (report_id, title, report_group, period_label, format, updated_at, row_count, owner_name, status, summary)
SELECT 'r-density', 'Отклонение плотности по ТТН', 'Инциденты', '01–03.08.2026', 'PDF', TIMESTAMPTZ '2026-08-03 12:05:00+03', 3, 'Качество', 'готов',
       'Расхождения плотности документ / факт по поставкам'
WHERE NOT EXISTS (SELECT 1 FROM oc_kpi_report WHERE report_id='r-density');
INSERT INTO oc_kpi_report (report_id, title, report_group, period_label, format, updated_at, row_count, owner_name, status, summary)
SELECT 'r-recv', 'Поставки в РГС без приёмки', 'Инциденты', 'Июль–август 2026', 'Excel', TIMESTAMPTZ '2026-08-03 15:55:00+03', 58, 'Эксплуатация', 'ошибка',
       'События поступления без оформления приёмки на АЗС'
WHERE NOT EXISTS (SELECT 1 FROM oc_kpi_report WHERE report_id='r-recv');

-- вагоны / вторичная логистика
INSERT INTO oc_wagon_trip (id, wagon_no, product_code, from_station, to_station, waybill, shipped_at, current_station, operation, op_at, km_left, delay_bucket)
SELECT 'f1000000-0000-4000-8000-000000000001', '54821716', 'АИ-92', 'Комбинатская', 'Егоршино', 'ЭЮ291847',
       TIMESTAMPTZ '2026-08-01 09:20:00+03', 'Кунара', 'Проследование', TIMESTAMPTZ '2026-08-03 11:05:00+03', 86, 'lt1'
WHERE NOT EXISTS (SELECT 1 FROM oc_wagon_trip WHERE id='f1000000-0000-4000-8000-000000000001');
INSERT INTO oc_wagon_trip (id, wagon_no, product_code, from_station, to_station, waybill, shipped_at, current_station, operation, op_at, km_left, delay_bucket)
SELECT 'f1000000-0000-4000-8000-000000000002', '54821902', 'ДТ', 'Омск-Северный', 'Барнаул', 'ЭЮ291901',
       TIMESTAMPTZ '2026-07-28 18:40:00+03', 'Алтайская', 'Простой', TIMESTAMPTZ '2026-08-02 22:10:00+03', 12, 'gt3'
WHERE NOT EXISTS (SELECT 1 FROM oc_wagon_trip WHERE id='f1000000-0000-4000-8000-000000000002');
INSERT INTO oc_wagon_trip (id, wagon_no, product_code, from_station, to_station, waybill, shipped_at, current_station, operation, op_at, km_left, delay_bucket)
SELECT 'f1000000-0000-4000-8000-000000000003', '54822044', 'АИ-95', 'Ярославль-Главный', 'Москва-Товарная', 'ЭЮ292011',
       TIMESTAMPTZ '2026-08-02 14:05:00+03', 'Александров', 'Ожидание', TIMESTAMPTZ '2026-08-03 08:30:00+03', 118, 'lt1'
WHERE NOT EXISTS (SELECT 1 FROM oc_wagon_trip WHERE id='f1000000-0000-4000-8000-000000000003');

-- партии НП для АЗС (доп. к legacy TS-1)
INSERT INTO oc_batch (batch_id, product_code, origin, volume_l, status, quality_status)
SELECT 'AI92-2026-041', 'АИ-92', 'НБ Екатеринбург', 42000, 'active', 'ok'
WHERE NOT EXISTS (SELECT 1 FROM oc_batch WHERE batch_id='AI92-2026-041');
INSERT INTO oc_batch (batch_id, product_code, origin, volume_l, status, quality_status)
SELECT 'DT-2026-018', 'ДТ', 'НБ Омск', 38000, 'active', 'pending'
WHERE NOT EXISTS (SELECT 1 FROM oc_batch WHERE batch_id='DT-2026-018');

-- транспорт вторичной логистики
INSERT INTO oc_vehicle (vehicle_code, vehicle_type, plate_no, capacity_l, status)
SELECT 'AC-014', 'truck', 'А014ЕК66', 30000, 'en_route'
WHERE NOT EXISTS (SELECT 1 FROM oc_vehicle WHERE vehicle_code='AC-014');
INSERT INTO oc_vehicle (vehicle_code, vehicle_type, plate_no, capacity_l, status)
SELECT 'AC-870', 'truck', 'К870КР23', 28000, 'loading'
WHERE NOT EXISTS (SELECT 1 FROM oc_vehicle WHERE vehicle_code='AC-870');

-- активы СИ на АЗС
INSERT INTO oc_asset (asset_code, asset_type, display_name, manufacturer, serial_no, status, metrology_class, next_verification, site_code)
SELECT 'LVL-014-1', 'level_gauge', 'Уровнемер РГС-1 АЗС 014', 'Струна', 'LVL-014-1', 'active', '0.15%', CURRENT_DATE + 90, 'azs-014'
WHERE NOT EXISTS (SELECT 1 FROM oc_asset WHERE asset_code='LVL-014-1');
INSERT INTO oc_asset (asset_code, asset_type, display_name, manufacturer, serial_no, status, metrology_class, next_verification, site_code)
SELECT 'TRK-005-3', 'trk', 'ТРК колонка 3 АЗС 005', 'Топаз', 'TRK-005-3', 'active', '0.25%', CURRENT_DATE + 25, 'azs-005'
WHERE NOT EXISTS (SELECT 1 FROM oc_asset WHERE asset_code='TRK-005-3');

-- инциденты / аномалии АЗС
INSERT INTO oc_anomaly (id, zone_code, tank_code, anomaly_type, severity, title, details, status)
SELECT 'a2000000-0000-4000-8000-000000000001', 'z_rgs_trk', 'AZS-014-1', 'rgs_trk', 'yellow',
       'Отклонение РГС–ТРК АЗС 014', 'Относительное отклонение >2%', 'open'
WHERE NOT EXISTS (SELECT 1 FROM oc_anomaly WHERE id='a2000000-0000-4000-8000-000000000001');
INSERT INTO oc_incident (id, incident_no, module, severity, title, description, asset_code, zone_code, status, assignee)
SELECT 'a3000000-0000-4000-8000-000000000001', 'INC-2026-104', 'azs', 'red',
       'Погрешность ТРК выше допуска · АЗС 005', 'Колонка 3, требуется поверка',
       'TRK-005-3', 'z_rgs_trk', 'assigned', 'Смирнов И.'
WHERE NOT EXISTS (SELECT 1 FROM oc_incident WHERE id='a3000000-0000-4000-8000-000000000001');

-- B2B / партнёры сети
INSERT INTO oc_partner (partner_code, display_name, partner_type, contact)
SELECT 'NB-EKB', 'Нефтебаза Екатеринбург', 'depot', 'nb-ekb@example.ru'
WHERE NOT EXISTS (SELECT 1 FROM oc_partner WHERE partner_code='NB-EKB');
INSERT INTO oc_b2b_order (id, order_no, partner_code, product_code, volume_l, status)
SELECT 'a4000000-0000-4000-8000-000000000001', 'B2B-AZS-210', 'NB-EKB', 'АИ-92', 45000, 'confirmed'
WHERE NOT EXISTS (SELECT 1 FROM oc_b2b_order WHERE id='a4000000-0000-4000-8000-000000000001');

-- снимки баланса для зон АЗС
INSERT INTO oc_balance_snapshot (id, zone_code, period_start, period_end, inflow_l, outflow_l, stock_start_l, stock_end_l, imbalance_l, imbalance_pct, status)
SELECT 'a5000000-0000-4000-8000-000000000001', 'z_nb_rgs', date_trunc('day', NOW()), NOW(), 98000, 96500, 210000, 211200, 300, 0.31, 'yellow'
WHERE NOT EXISTS (SELECT 1 FROM oc_balance_snapshot WHERE id='a5000000-0000-4000-8000-000000000001');
INSERT INTO oc_balance_snapshot (id, zone_code, period_start, period_end, inflow_l, outflow_l, stock_start_l, stock_end_l, imbalance_l, imbalance_pct, status)
SELECT 'a5000000-0000-4000-8000-000000000002', 'z_rgs_trk', date_trunc('day', NOW()), NOW(), 72000, 71850, 180000, 179900, -150, 0.21, 'green'
WHERE NOT EXISTS (SELECT 1 FROM oc_balance_snapshot WHERE id='a5000000-0000-4000-8000-000000000002');
