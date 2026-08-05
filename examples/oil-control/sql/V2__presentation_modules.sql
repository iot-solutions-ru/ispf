-- === УПРАВЛЕНИЕ ТЗК: дебалансы / аномалии ===
CREATE TABLE IF NOT EXISTS oc_anomaly (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  zone_code VARCHAR(32),
  tank_code VARCHAR(32),
  anomaly_type VARCHAR(64) NOT NULL,
  severity VARCHAR(16) NOT NULL DEFAULT 'yellow',
  title VARCHAR(256) NOT NULL,
  details TEXT,
  detected_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  status VARCHAR(32) NOT NULL DEFAULT 'open',
  closed_at TIMESTAMPTZ
);

-- seed balance snapshots (green/yellow demo)
INSERT INTO oc_balance_snapshot (id, zone_code, period_start, period_end, inflow_l, outflow_l, stock_start_l, stock_end_l, imbalance_l, imbalance_pct, status)
SELECT '22222222-2222-2222-2222-222222222201', 'z1_rail_rvs', date_trunc('day', NOW()), NOW(), 120000, 119500, 80000, 80400, 100, 0.08, 'green'
WHERE NOT EXISTS (SELECT 1 FROM oc_balance_snapshot WHERE id='22222222-2222-2222-2222-222222222201');
INSERT INTO oc_balance_snapshot (id, zone_code, period_start, period_end, inflow_l, outflow_l, stock_start_l, stock_end_l, imbalance_l, imbalance_pct, status)
SELECT '22222222-2222-2222-2222-222222222202', 'z2_rvs_ac', date_trunc('day', NOW()), NOW(), 45000, 44800, 50000, 50150, 50, 0.11, 'green'
WHERE NOT EXISTS (SELECT 1 FROM oc_balance_snapshot WHERE id='22222222-2222-2222-2222-222222222202');
INSERT INTO oc_balance_snapshot (id, zone_code, period_start, period_end, inflow_l, outflow_l, stock_start_l, stock_end_l, imbalance_l, imbalance_pct, status)
SELECT '22222222-2222-2222-2222-222222222203', 'z4_rvs_tza', date_trunc('day', NOW()), NOW(), 30000, 29200, 60000, 60500, 300, 1.0, 'red'
WHERE NOT EXISTS (SELECT 1 FROM oc_balance_snapshot WHERE id='22222222-2222-2222-2222-222222222203');

INSERT INTO oc_anomaly (id, zone_code, tank_code, anomaly_type, severity, title, details, status)
SELECT '33333333-3333-3333-3333-333333333301', 'z4_rvs_tza', 'RVS-4', 'imbalance', 'red', 'Дебаланс участка РВС-ТЗА', 'Превышение норматива потерь >0.5%', 'open'
WHERE NOT EXISTS (SELECT 1 FROM oc_anomaly WHERE id='33333333-3333-3333-3333-333333333301');
INSERT INTO oc_anomaly (id, zone_code, tank_code, anomaly_type, severity, title, details, status)
SELECT '33333333-3333-3333-3333-333333333302', 'z2_rvs_ac', 'RVS-1', 'theft_suspect', 'yellow', 'Аномальный расход без операции', 'Резкое снижение уровня без фиксации налива', 'open'
WHERE NOT EXISTS (SELECT 1 FROM oc_anomaly WHERE id='33333333-3333-3333-3333-333333333302');

-- === КОНТРОЛЬ КАЧЕСТВА ===
CREATE TABLE IF NOT EXISTS oc_batch (
  batch_id VARCHAR(64) PRIMARY KEY,
  product_code VARCHAR(16) NOT NULL DEFAULT 'TS-1',
  origin VARCHAR(128),
  received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  volume_l DOUBLE PRECISION,
  status VARCHAR(32) NOT NULL DEFAULT 'active',
  quality_status VARCHAR(32) NOT NULL DEFAULT 'pending'
);
CREATE TABLE IF NOT EXISTS oc_quality_passport (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  batch_id VARCHAR(64) NOT NULL REFERENCES oc_batch(batch_id),
  density_kg_m3 DOUBLE PRECISION,
  flash_point_c DOUBLE PRECISION,
  freezing_point_c DOUBLE PRECISION,
  water_ppm DOUBLE PRECISION,
  particulate_mg_l DOUBLE PRECISION,
  lab_name VARCHAR(128),
  tested_at TIMESTAMPTZ,
  conclusion VARCHAR(32) NOT NULL DEFAULT 'pending',
  certificate_no VARCHAR(64)
);
CREATE TABLE IF NOT EXISTS oc_lab_sample (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  batch_id VARCHAR(64) REFERENCES oc_batch(batch_id),
  tank_code VARCHAR(32),
  sample_point VARCHAR(128),
  sampled_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  operator_name VARCHAR(128),
  status VARCHAR(32) NOT NULL DEFAULT 'in_lab'
);
CREATE TABLE IF NOT EXISTS oc_antimix_event (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tank_code VARCHAR(32) NOT NULL,
  expected_product VARCHAR(16) NOT NULL,
  detected_product VARCHAR(16),
  event_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  severity VARCHAR(16) NOT NULL DEFAULT 'red',
  status VARCHAR(32) NOT NULL DEFAULT 'open',
  details TEXT
);

INSERT INTO oc_batch (batch_id, product_code, origin, volume_l, status, quality_status)
SELECT 'TS1-2026-081', 'TS-1', 'НПЗ Ангарск', 85000, 'active', 'ok'
WHERE NOT EXISTS (SELECT 1 FROM oc_batch WHERE batch_id='TS1-2026-081');
INSERT INTO oc_batch (batch_id, product_code, origin, volume_l, status, quality_status)
SELECT 'TS1-2026-082', 'TS-1', 'НПЗ Омск', 42000, 'active', 'pending'
WHERE NOT EXISTS (SELECT 1 FROM oc_batch WHERE batch_id='TS1-2026-082');
INSERT INTO oc_quality_passport (id, batch_id, density_kg_m3, flash_point_c, freezing_point_c, water_ppm, particulate_mg_l, lab_name, tested_at, conclusion, certificate_no)
SELECT '44444444-4444-4444-4444-444444444401', 'TS1-2026-081', 785.2, 42, -52, 18, 0.4, 'Лаб. Улан-Удэ', NOW() - INTERVAL '1 day', 'pass', 'ПК-081/26'
WHERE NOT EXISTS (SELECT 1 FROM oc_quality_passport WHERE id='44444444-4444-4444-4444-444444444401');
INSERT INTO oc_lab_sample (id, batch_id, tank_code, sample_point, operator_name, status)
SELECT '55555555-5555-5555-5555-555555555501', 'TS1-2026-082', 'RVS-2', 'Верхний слой', 'Иванов', 'in_lab'
WHERE NOT EXISTS (SELECT 1 FROM oc_lab_sample WHERE id='55555555-5555-5555-5555-555555555501');
INSERT INTO oc_antimix_event (id, tank_code, expected_product, detected_product, severity, status, details)
SELECT '66666666-6666-6666-6666-666666666601', 'RGS-1A', 'TS-1', NULL, 'yellow', 'open', 'Подозрение на смешение при приёме — требуется проба'
WHERE NOT EXISTS (SELECT 1 FROM oc_antimix_event WHERE id='66666666-6666-6666-6666-666666666601');

-- === ЛОГИСТИКА ===
CREATE TABLE IF NOT EXISTS oc_vehicle (
  vehicle_code VARCHAR(32) PRIMARY KEY,
  vehicle_type VARCHAR(32) NOT NULL,
  plate_no VARCHAR(32),
  capacity_l DOUBLE PRECISION,
  status VARCHAR(32) NOT NULL DEFAULT 'idle',
  last_lat DOUBLE PRECISION,
  last_lon DOUBLE PRECISION,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS oc_trip (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  vehicle_code VARCHAR(32) REFERENCES oc_vehicle(vehicle_code),
  trip_type VARCHAR(32) NOT NULL,
  from_site VARCHAR(64),
  to_site VARCHAR(64),
  planned_start TIMESTAMPTZ,
  planned_end TIMESTAMPTZ,
  actual_start TIMESTAMPTZ,
  actual_end TIMESTAMPTZ,
  volume_l DOUBLE PRECISION,
  status VARCHAR(32) NOT NULL DEFAULT 'planned',
  route_note VARCHAR(256)
);
CREATE TABLE IF NOT EXISTS oc_supply_plan (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  plan_date DATE NOT NULL,
  product_code VARCHAR(16) NOT NULL DEFAULT 'TS-1',
  planned_volume_l DOUBLE PRECISION NOT NULL,
  source VARCHAR(128),
  status VARCHAR(32) NOT NULL DEFAULT 'draft',
  note VARCHAR(256)
);

INSERT INTO oc_vehicle (vehicle_code, vehicle_type, plate_no, capacity_l, status, last_lat, last_lon)
SELECT 'TZA-01', 'tza', 'А001УУ03', 20000, 'en_route', 51.833, 107.583
WHERE NOT EXISTS (SELECT 1 FROM oc_vehicle WHERE vehicle_code='TZA-01');
INSERT INTO oc_vehicle (vehicle_code, vehicle_type, plate_no, capacity_l, status, last_lat, last_lon)
SELECT 'AC-12', 'truck', 'В112УУ03', 30000, 'loading', 51.840, 107.590
WHERE NOT EXISTS (SELECT 1 FROM oc_vehicle WHERE vehicle_code='AC-12');
INSERT INTO oc_vehicle (vehicle_code, vehicle_type, plate_no, capacity_l, status)
SELECT 'ZHD-8841', 'rail_tank', NULL, 60000, 'at_rail'
WHERE NOT EXISTS (SELECT 1 FROM oc_vehicle WHERE vehicle_code='ZHD-8841');
INSERT INTO oc_trip (id, vehicle_code, trip_type, from_site, to_site, planned_start, volume_l, status, route_note)
SELECT '77777777-7777-7777-7777-777777777701', 'TZA-01', 'apron_fueling', 'expense', 'apron', NOW(), 12000, 'in_progress', 'Рейс SU-1201'
WHERE NOT EXISTS (SELECT 1 FROM oc_trip WHERE id='77777777-7777-7777-7777-777777777701');
INSERT INTO oc_trip (id, vehicle_code, trip_type, from_site, to_site, planned_start, volume_l, status, route_note)
SELECT '77777777-7777-7777-7777-777777777702', 'AC-12', 'transfer', 'rail', 'expense', NOW() + INTERVAL '2 hours', 28000, 'planned', 'Перевозка прирельс→расходный'
WHERE NOT EXISTS (SELECT 1 FROM oc_trip WHERE id='77777777-7777-7777-7777-777777777702');
INSERT INTO oc_supply_plan (id, plan_date, planned_volume_l, source, status, note)
SELECT '88888888-8888-8888-8888-888888888801', CURRENT_DATE + 1, 90000, 'НПЗ Ангарск / ЖД', 'approved', 'Прогноз на завтра'
WHERE NOT EXISTS (SELECT 1 FROM oc_supply_plan WHERE id='88888888-8888-8888-8888-888888888801');

-- === УПРАВЛЕНИЕ АКТИВАМИ ===
CREATE TABLE IF NOT EXISTS oc_asset (
  asset_code VARCHAR(64) PRIMARY KEY,
  asset_type VARCHAR(32) NOT NULL,
  display_name VARCHAR(128) NOT NULL,
  manufacturer VARCHAR(128),
  serial_no VARCHAR(64),
  install_date DATE,
  status VARCHAR(32) NOT NULL DEFAULT 'active',
  metrology_class VARCHAR(32),
  next_verification DATE,
  site_code VARCHAR(32)
);
CREATE TABLE IF NOT EXISTS oc_asset_service (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  asset_code VARCHAR(64) NOT NULL REFERENCES oc_asset(asset_code),
  service_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  service_type VARCHAR(64) NOT NULL,
  performed_by VARCHAR(128),
  result VARCHAR(32),
  note TEXT
);
CREATE TABLE IF NOT EXISTS oc_incident (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  incident_no VARCHAR(32) NOT NULL UNIQUE,
  module VARCHAR(32) NOT NULL,
  severity VARCHAR(16) NOT NULL DEFAULT 'yellow',
  title VARCHAR(256) NOT NULL,
  description TEXT,
  asset_code VARCHAR(64),
  zone_code VARCHAR(32),
  opened_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  status VARCHAR(32) NOT NULL DEFAULT 'new',
  assignee VARCHAR(128),
  closed_at TIMESTAMPTZ
);
CREATE TABLE IF NOT EXISTS oc_work_order (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  wo_no VARCHAR(32) NOT NULL UNIQUE,
  incident_id UUID REFERENCES oc_incident(id),
  title VARCHAR(256) NOT NULL,
  assignee VARCHAR(128),
  priority VARCHAR(16) NOT NULL DEFAULT 'normal',
  status VARCHAR(32) NOT NULL DEFAULT 'open',
  due_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS oc_partner (
  partner_code VARCHAR(32) PRIMARY KEY,
  display_name VARCHAR(128) NOT NULL,
  partner_type VARCHAR(32) NOT NULL,
  contact VARCHAR(128)
);
CREATE TABLE IF NOT EXISTS oc_b2b_order (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  order_no VARCHAR(32) NOT NULL UNIQUE,
  partner_code VARCHAR(32) REFERENCES oc_partner(partner_code),
  product_code VARCHAR(16) NOT NULL DEFAULT 'TS-1',
  volume_l DOUBLE PRECISION NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'requested',
  requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO oc_asset (asset_code, asset_type, display_name, manufacturer, serial_no, status, metrology_class, next_verification, site_code)
SELECT 'MET-PPV-1', 'meter', 'ППВ приём', 'ПромПрибор', 'PPV-9912', 'active', '0.15%', CURRENT_DATE + 120, 'rail'
WHERE NOT EXISTS (SELECT 1 FROM oc_asset WHERE asset_code='MET-PPV-1');
INSERT INTO oc_asset (asset_code, asset_type, display_name, manufacturer, serial_no, status, metrology_class, next_verification, site_code)
SELECT 'MET-SPV-1', 'meter', 'ШПВ налив ТЗА', 'ПромПрибор', 'SPV-4410', 'active', '0.15%', CURRENT_DATE + 40, 'expense'
WHERE NOT EXISTS (SELECT 1 FROM oc_asset WHERE asset_code='MET-SPV-1');
INSERT INTO oc_asset (asset_code, asset_type, display_name, manufacturer, serial_no, status, metrology_class, next_verification, site_code)
SELECT 'TANK-RVS-1', 'tank', 'РВС-1', NULL, NULL, 'active', NULL, NULL, 'rail'
WHERE NOT EXISTS (SELECT 1 FROM oc_asset WHERE asset_code='TANK-RVS-1');
INSERT INTO oc_asset_service (id, asset_code, service_type, performed_by, result, note)
SELECT '99999999-9999-9999-9999-999999999901', 'MET-PPV-1', 'verification', 'ЦСМ', 'pass', 'Очередная поверка'
WHERE NOT EXISTS (SELECT 1 FROM oc_asset_service WHERE id='99999999-9999-9999-9999-999999999901');
INSERT INTO oc_incident (id, incident_no, module, severity, title, description, asset_code, zone_code, status, assignee)
SELECT 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01', 'INC-2026-014', 'tzk', 'red', 'Критический дебаланс РВС-ТЗА', 'Требуется расследование потерь', 'MET-SPV-1', 'z4_rvs_tza', 'assigned', 'Петров А.'
WHERE NOT EXISTS (SELECT 1 FROM oc_incident WHERE id='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01');
INSERT INTO oc_incident (id, incident_no, module, severity, title, description, status, assignee)
SELECT 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa02', 'INC-2026-015', 'quality', 'yellow', 'Антимикс: контроль РГС-1А', 'Ожидание результата пробы', 'new', NULL
WHERE NOT EXISTS (SELECT 1 FROM oc_incident WHERE id='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa02');
INSERT INTO oc_work_order (id, wo_no, incident_id, title, assignee, priority, status, due_at)
SELECT 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01', 'WO-014-1', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01', 'Проверить ШПВ и журнал наливов', 'Петров А.', 'high', 'open', NOW() + INTERVAL '8 hours'
WHERE NOT EXISTS (SELECT 1 FROM oc_work_order WHERE id='bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01');
INSERT INTO oc_partner (partner_code, display_name, partner_type, contact)
SELECT 'AIRLINE-SU', 'Авиакомпания-партнёр', 'airline', 'ops@example.ru'
WHERE NOT EXISTS (SELECT 1 FROM oc_partner WHERE partner_code='AIRLINE-SU');
INSERT INTO oc_b2b_order (id, order_no, partner_code, volume_l, status)
SELECT 'cccccccc-cccc-cccc-cccc-cccccccccc01', 'B2B-10021', 'AIRLINE-SU', 15000, 'confirmed'
WHERE NOT EXISTS (SELECT 1 FROM oc_b2b_order WHERE id='cccccccc-cccc-cccc-cccc-cccccccccc01');

-- KPI helper view-like seed not needed; queries compute live
