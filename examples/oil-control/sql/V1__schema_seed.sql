CREATE TABLE IF NOT EXISTS oc_site (
  site_code VARCHAR(32) PRIMARY KEY,
  display_name VARCHAR(128) NOT NULL,
  site_type VARCHAR(32) NOT NULL
);
CREATE TABLE IF NOT EXISTS oc_zone (
  zone_code VARCHAR(32) PRIMARY KEY,
  display_name VARCHAR(128) NOT NULL,
  site_code VARCHAR(32) REFERENCES oc_site(site_code),
  inflow_desc VARCHAR(256),
  outflow_desc VARCHAR(256),
  calc_period VARCHAR(32) NOT NULL DEFAULT 'shift',
  loss_norm_pct DOUBLE PRECISION NOT NULL DEFAULT 0.2,
  sort_order INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS oc_tank (
  tank_code VARCHAR(32) PRIMARY KEY,
  display_name VARCHAR(128) NOT NULL,
  site_code VARCHAR(32) REFERENCES oc_site(site_code),
  tank_kind VARCHAR(16) NOT NULL DEFAULT 'RVS',
  max_level_cm DOUBLE PRECISION NOT NULL DEFAULT 1000,
  product_code VARCHAR(16) NOT NULL DEFAULT 'TS-1',
  active BOOLEAN NOT NULL DEFAULT true
);
CREATE TABLE IF NOT EXISTS oc_calibration (
  id UUID PRIMARY KEY,
  tank_code VARCHAR(32) NOT NULL REFERENCES oc_tank(tank_code),
  version_no INTEGER NOT NULL DEFAULT 1,
  valid_from DATE,
  valid_to DATE,
  status VARCHAR(32) NOT NULL DEFAULT 'active',
  source_name VARCHAR(256),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (tank_code, version_no)
);
CREATE TABLE IF NOT EXISTS oc_calibration_row (
  id BIGSERIAL PRIMARY KEY,
  calibration_id UUID NOT NULL REFERENCES oc_calibration(id) ON DELETE CASCADE,
  level_cm DOUBLE PRECISION NOT NULL,
  volume_l DOUBLE PRECISION NOT NULL
);
CREATE TABLE IF NOT EXISTS oc_measurement (
  id UUID PRIMARY KEY,
  tank_code VARCHAR(32) NOT NULL REFERENCES oc_tank(tank_code),
  measured_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  level_cm DOUBLE PRECISION NOT NULL,
  temperature_c DOUBLE PRECISION NOT NULL,
  volume_l DOUBLE PRECISION,
  volume_l_15c DOUBLE PRECISION,
  operator_name VARCHAR(128) NOT NULL,
  lat DOUBLE PRECISION,
  lon DOUBLE PRECISION,
  note VARCHAR(512),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS oc_meter (
  meter_code VARCHAR(32) PRIMARY KEY,
  display_name VARCHAR(128) NOT NULL,
  zone_code VARCHAR(32) REFERENCES oc_zone(zone_code),
  meter_role VARCHAR(32) NOT NULL,
  unit VARCHAR(16) NOT NULL DEFAULT 'L'
);
CREATE TABLE IF NOT EXISTS oc_meter_reading (
  id BIGSERIAL PRIMARY KEY,
  meter_code VARCHAR(32) NOT NULL REFERENCES oc_meter(meter_code),
  reading_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  total_l DOUBLE PRECISION NOT NULL,
  delta_l DOUBLE PRECISION
);
CREATE TABLE IF NOT EXISTS oc_balance_snapshot (
  id UUID PRIMARY KEY,
  zone_code VARCHAR(32) NOT NULL REFERENCES oc_zone(zone_code),
  period_start TIMESTAMPTZ NOT NULL,
  period_end TIMESTAMPTZ NOT NULL,
  inflow_l DOUBLE PRECISION NOT NULL DEFAULT 0,
  outflow_l DOUBLE PRECISION NOT NULL DEFAULT 0,
  stock_start_l DOUBLE PRECISION NOT NULL DEFAULT 0,
  stock_end_l DOUBLE PRECISION NOT NULL DEFAULT 0,
  imbalance_l DOUBLE PRECISION NOT NULL DEFAULT 0,
  imbalance_pct DOUBLE PRECISION NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'green',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO oc_site (site_code, display_name, site_type)
SELECT 'rail', 'Прирельсовый склад', 'rail' WHERE NOT EXISTS (SELECT 1 FROM oc_site WHERE site_code='rail');
INSERT INTO oc_site (site_code, display_name, site_type)
SELECT 'expense', 'Расходный склад', 'expense' WHERE NOT EXISTS (SELECT 1 FROM oc_site WHERE site_code='expense');
INSERT INTO oc_site (site_code, display_name, site_type)
SELECT 'apron', 'Перрон', 'apron' WHERE NOT EXISTS (SELECT 1 FROM oc_site WHERE site_code='apron');

INSERT INTO oc_zone (zone_code, display_name, site_code, inflow_desc, outflow_desc, calc_period, loss_norm_pct, sort_order)
SELECT 'z1_rail_rvs', 'ЖД - РВС (Прирельсовый)', 'rail', 'ЖД цистерны (ручной ввод)', 'Налив в АЦ', 'shift', 0.2, 1
WHERE NOT EXISTS (SELECT 1 FROM oc_zone WHERE zone_code='z1_rail_rvs');
INSERT INTO oc_zone (zone_code, display_name, site_code, inflow_desc, outflow_desc, calc_period, loss_norm_pct, sort_order)
SELECT 'z2_rvs_ac', 'РВС - АЦ (Прирельсовый)', 'rail', 'Остатки РВС 1-3', 'Налив в АЦ', 'shift', 0.2, 2
WHERE NOT EXISTS (SELECT 1 FROM oc_zone WHERE zone_code='z2_rvs_ac');
INSERT INTO oc_zone (zone_code, display_name, site_code, inflow_desc, outflow_desc, calc_period, loss_norm_pct, sort_order)
SELECT 'z3_ac_rvs', 'АЦ - РВС (Расходный)', 'expense', 'Слив из АЦ (ВЖУ)', 'Остатки РВС 4-5, РГС 1А-8А', 'shift', 0.2, 3
WHERE NOT EXISTS (SELECT 1 FROM oc_zone WHERE zone_code='z3_ac_rvs');
INSERT INTO oc_zone (zone_code, display_name, site_code, inflow_desc, outflow_desc, calc_period, loss_norm_pct, sort_order)
SELECT 'z4_rvs_tza', 'РВС - ТЗА (Расходный)', 'expense', 'Остатки РВС 4-5, РГС 1А-8А', 'Налив в ТЗА (ШПВ)', 'shift', 0.2, 4
WHERE NOT EXISTS (SELECT 1 FROM oc_zone WHERE zone_code='z4_rvs_tza');
INSERT INTO oc_zone (zone_code, display_name, site_code, inflow_desc, outflow_desc, calc_period, loss_norm_pct, sort_order)
SELECT 'z5_tza_ac', 'ТЗА - ВС (Перрон)', 'apron', 'Налив в ТЗА (ШПВ)', 'Заправка ВС (счетчики ТЗА)', 'flight_shift', 0.2, 5
WHERE NOT EXISTS (SELECT 1 FROM oc_zone WHERE zone_code='z5_tza_ac');

INSERT INTO oc_tank (tank_code, display_name, site_code, tank_kind, max_level_cm)
SELECT 'RVS-1', 'РВС-1', 'rail', 'RVS', 1200 WHERE NOT EXISTS (SELECT 1 FROM oc_tank WHERE tank_code='RVS-1');
INSERT INTO oc_tank (tank_code, display_name, site_code, tank_kind, max_level_cm)
SELECT 'RVS-2', 'РВС-2', 'rail', 'RVS', 1200 WHERE NOT EXISTS (SELECT 1 FROM oc_tank WHERE tank_code='RVS-2');
INSERT INTO oc_tank (tank_code, display_name, site_code, tank_kind, max_level_cm)
SELECT 'RVS-3', 'РВС-3', 'rail', 'RVS', 1200 WHERE NOT EXISTS (SELECT 1 FROM oc_tank WHERE tank_code='RVS-3');
INSERT INTO oc_tank (tank_code, display_name, site_code, tank_kind, max_level_cm)
SELECT 'RVS-4', 'РВС-4', 'expense', 'RVS', 1200 WHERE NOT EXISTS (SELECT 1 FROM oc_tank WHERE tank_code='RVS-4');
INSERT INTO oc_tank (tank_code, display_name, site_code, tank_kind, max_level_cm)
SELECT 'RVS-5', 'РВС-5', 'expense', 'RVS', 1200 WHERE NOT EXISTS (SELECT 1 FROM oc_tank WHERE tank_code='RVS-5');
INSERT INTO oc_tank (tank_code, display_name, site_code, tank_kind, max_level_cm)
SELECT 'RGS-1A', 'РГС-1А', 'expense', 'RGS', 400 WHERE NOT EXISTS (SELECT 1 FROM oc_tank WHERE tank_code='RGS-1A');
INSERT INTO oc_tank (tank_code, display_name, site_code, tank_kind, max_level_cm)
SELECT 'RGS-2A', 'РГС-2А', 'expense', 'RGS', 400 WHERE NOT EXISTS (SELECT 1 FROM oc_tank WHERE tank_code='RGS-2A');

INSERT INTO oc_meter (meter_code, display_name, zone_code, meter_role)
SELECT 'PPV-1', 'ППВ (приём)', 'z1_rail_rvs', 'inflow' WHERE NOT EXISTS (SELECT 1 FROM oc_meter WHERE meter_code='PPV-1');
INSERT INTO oc_meter (meter_code, display_name, zone_code, meter_role)
SELECT 'VZU-1', 'ВЖУ (слив АЦ)', 'z3_ac_rvs', 'inflow' WHERE NOT EXISTS (SELECT 1 FROM oc_meter WHERE meter_code='VZU-1');
INSERT INTO oc_meter (meter_code, display_name, zone_code, meter_role)
SELECT 'SPV-1', 'ШПВ (налив ТЗА)', 'z4_rvs_tza', 'outflow' WHERE NOT EXISTS (SELECT 1 FROM oc_meter WHERE meter_code='SPV-1');

INSERT INTO oc_measurement (id, tank_code, measured_at, level_cm, temperature_c, volume_l, operator_name, note)
SELECT '11111111-1111-1111-1111-111111111101', 'RVS-1', NOW() - INTERVAL '2 hours', 642, 8.5, NULL, 'demo', 'seed'
WHERE NOT EXISTS (SELECT 1 FROM oc_measurement WHERE id='11111111-1111-1111-1111-111111111101');
INSERT INTO oc_measurement (id, tank_code, measured_at, level_cm, temperature_c, volume_l, operator_name, note)
SELECT '11111111-1111-1111-1111-111111111102', 'RVS-2', NOW() - INTERVAL '2 hours', 510, 8.2, NULL, 'demo', 'seed'
WHERE NOT EXISTS (SELECT 1 FROM oc_measurement WHERE id='11111111-1111-1111-1111-111111111102');
INSERT INTO oc_measurement (id, tank_code, measured_at, level_cm, temperature_c, volume_l, operator_name, note)
SELECT '11111111-1111-1111-1111-111111111103', 'RVS-4', NOW() - INTERVAL '1 hours', 780, 9.1, NULL, 'demo', 'seed'
WHERE NOT EXISTS (SELECT 1 FROM oc_measurement WHERE id='11111111-1111-1111-1111-111111111103');
