-- V6: полный контур под React SPA (станция, ТРК, слив, KPI, ряды, прогноз, регионы НП)

CREATE TABLE IF NOT EXISTS oc_region_fuel (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  region_code VARCHAR(16) NOT NULL REFERENCES oc_region(region_code),
  product_code VARCHAR(32) NOT NULL,
  fill_pct DOUBLE PRECISION NOT NULL DEFAULT 0,
  critical_count INTEGER NOT NULL DEFAULT 0,
  UNIQUE (region_code, product_code)
);

CREATE TABLE IF NOT EXISTS oc_station_tank_live (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  azs_code VARCHAR(8) NOT NULL,
  tank_no INTEGER NOT NULL,
  product_code VARCHAR(32) NOT NULL,
  level_cm DOUBLE PRECISION,
  volume_l DOUBLE PRECISION,
  fill_pct DOUBLE PRECISION,
  temp_c DOUBLE PRECISION,
  density_kg_m3 DOUBLE PRECISION,
  water_cm DOUBLE PRECISION,
  status VARCHAR(32) NOT NULL DEFAULT 'норма',
  UNIQUE (azs_code, tank_no)
);

CREATE TABLE IF NOT EXISTS oc_station_pump (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  azs_code VARCHAR(8) NOT NULL,
  pump_no INTEGER NOT NULL,
  product_code VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'в работе',
  total_l DOUBLE PRECISION NOT NULL DEFAULT 0,
  shift_l DOUBLE PRECISION NOT NULL DEFAULT 0,
  rate_lpm DOUBLE PRECISION,
  error_pct DOUBLE PRECISION,
  last_at TIMESTAMPTZ,
  UNIQUE (azs_code, pump_no)
);

CREATE TABLE IF NOT EXISTS oc_station_journal (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  azs_code VARCHAR(8) NOT NULL,
  journal_type VARCHAR(32) NOT NULL,
  entry_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  entry_no VARCHAR(32) NOT NULL,
  description TEXT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'открыт'
);

CREATE TABLE IF NOT EXISTS oc_discharge_session (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  azs_code VARCHAR(8) NOT NULL,
  ttn_no VARCHAR(64),
  started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  status VARCHAR(32) NOT NULL DEFAULT 'active',
  note TEXT
);

CREATE TABLE IF NOT EXISTS oc_discharge_tank (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID NOT NULL REFERENCES oc_discharge_session(id) ON DELETE CASCADE,
  sections VARCHAR(64),
  tank_no INTEGER NOT NULL,
  product_code VARCHAR(32) NOT NULL,
  received_kg DOUBLE PRECISION NOT NULL DEFAULT 0,
  plan_kg DOUBLE PRECISION NOT NULL DEFAULT 0,
  timer_label VARCHAR(16),
  volume_l DOUBLE PRECISION,
  fill_pct DOUBLE PRECISION,
  temp_c DOUBLE PRECISION,
  density DOUBLE PRECISION,
  density_15 DOUBLE PRECISION
);

CREATE TABLE IF NOT EXISTS oc_stock_forecast (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  bucket_label VARCHAR(16) NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0,
  fact_pct DOUBLE PRECISION,
  forecast_pct DOUBLE PRECISION,
  product_code VARCHAR(32)
);

CREATE TABLE IF NOT EXISTS oc_rgs_trk_series (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  bucket_label VARCHAR(32) NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0,
  rgs_l DOUBLE PRECISION NOT NULL,
  trk_l DOUBLE PRECISION NOT NULL
);

CREATE TABLE IF NOT EXISTS oc_balance_trend (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  bucket_label VARCHAR(32) NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0,
  imbalance_l DOUBLE PRECISION NOT NULL DEFAULT 0,
  inflow_l DOUBLE PRECISION,
  outflow_l DOUBLE PRECISION
);

CREATE TABLE IF NOT EXISTS oc_kpi_card (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  title VARCHAR(128) NOT NULL,
  total_label VARCHAR(64) NOT NULL,
  plan_label VARCHAR(64),
  segments_json TEXT NOT NULL DEFAULT '[]',
  legend VARCHAR(256),
  sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS oc_kpi_incident_summary (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  title VARCHAR(128) NOT NULL,
  count_n INTEGER NOT NULL DEFAULT 0,
  azs_count INTEGER NOT NULL DEFAULT 0,
  mass_label VARCHAR(64),
  action_label VARCHAR(128),
  colors_json TEXT NOT NULL DEFAULT '[]',
  sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS oc_calibration_profile (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  azs_code VARCHAR(8) NOT NULL,
  tank_no INTEGER NOT NULL,
  calibrated_at DATE,
  ok_levels INTEGER,
  total_levels INTEGER
);

CREATE TABLE IF NOT EXISTS oc_calibration_profile_row (
  id BIGSERIAL PRIMARY KEY,
  profile_id UUID NOT NULL REFERENCES oc_calibration_profile(id) ON DELETE CASCADE,
  level_cm DOUBLE PRECISION NOT NULL,
  viis_l DOUBLE PRECISION NOT NULL,
  vtoir_l DOUBLE PRECISION NOT NULL,
  dev_l DOUBLE PRECISION,
  dev_pct DOUBLE PRECISION
);

-- === seed region fuels ===
INSERT INTO oc_region_fuel (id, region_code, product_code, fill_pct, critical_count)
SELECT 'aa100000-0000-4000-8000-000000000001', 'ekb', 'АИ-92', 66, 2
WHERE NOT EXISTS (SELECT 1 FROM oc_region_fuel WHERE id='aa100000-0000-4000-8000-000000000001');
INSERT INTO oc_region_fuel (id, region_code, product_code, fill_pct, critical_count)
SELECT 'aa100000-0000-4000-8000-000000000002', 'ekb', 'АИ-95', 71, 0
WHERE NOT EXISTS (SELECT 1 FROM oc_region_fuel WHERE id='aa100000-0000-4000-8000-000000000002');
INSERT INTO oc_region_fuel (id, region_code, product_code, fill_pct, critical_count)
SELECT 'aa100000-0000-4000-8000-000000000003', 'ekb', 'ДТ', 74, 0
WHERE NOT EXISTS (SELECT 1 FROM oc_region_fuel WHERE id='aa100000-0000-4000-8000-000000000003');
INSERT INTO oc_region_fuel (id, region_code, product_code, fill_pct, critical_count)
SELECT 'aa100000-0000-4000-8000-000000000004', 'ekb', 'СУГ', 41, 3
WHERE NOT EXISTS (SELECT 1 FROM oc_region_fuel WHERE id='aa100000-0000-4000-8000-000000000004');
INSERT INTO oc_region_fuel (id, region_code, product_code, fill_pct, critical_count)
SELECT 'aa100000-0000-4000-8000-000000000005', 'kem', 'АИ-92', 54, 1
WHERE NOT EXISTS (SELECT 1 FROM oc_region_fuel WHERE id='aa100000-0000-4000-8000-000000000005');
INSERT INTO oc_region_fuel (id, region_code, product_code, fill_pct, critical_count)
SELECT 'aa100000-0000-4000-8000-000000000006', 'kem', 'АИ-95', 62, 0
WHERE NOT EXISTS (SELECT 1 FROM oc_region_fuel WHERE id='aa100000-0000-4000-8000-000000000006');
INSERT INTO oc_region_fuel (id, region_code, product_code, fill_pct, critical_count)
SELECT 'aa100000-0000-4000-8000-000000000007', 'kem', 'ДТ', 69, 0
WHERE NOT EXISTS (SELECT 1 FROM oc_region_fuel WHERE id='aa100000-0000-4000-8000-000000000007');
INSERT INTO oc_region_fuel (id, region_code, product_code, fill_pct, critical_count)
SELECT 'aa100000-0000-4000-8000-000000000008', 'kem', 'СУГ', 34, 4
WHERE NOT EXISTS (SELECT 1 FROM oc_region_fuel WHERE id='aa100000-0000-4000-8000-000000000008');
INSERT INTO oc_region_fuel (id, region_code, product_code, fill_pct, critical_count)
SELECT 'aa100000-0000-4000-8000-000000000009', 'krd', 'АИ-92', 78, 0
WHERE NOT EXISTS (SELECT 1 FROM oc_region_fuel WHERE id='aa100000-0000-4000-8000-000000000009');
INSERT INTO oc_region_fuel (id, region_code, product_code, fill_pct, critical_count)
SELECT 'aa100000-0000-4000-8000-000000000010', 'krd', 'АИ-95', 81, 0
WHERE NOT EXISTS (SELECT 1 FROM oc_region_fuel WHERE id='aa100000-0000-4000-8000-000000000010');
INSERT INTO oc_region_fuel (id, region_code, product_code, fill_pct, critical_count)
SELECT 'aa100000-0000-4000-8000-000000000011', 'krd', 'ДТ', 72, 1
WHERE NOT EXISTS (SELECT 1 FROM oc_region_fuel WHERE id='aa100000-0000-4000-8000-000000000011');
INSERT INTO oc_region_fuel (id, region_code, product_code, fill_pct, critical_count)
SELECT 'aa100000-0000-4000-8000-000000000012', 'krd', 'СУГ', 55, 2
WHERE NOT EXISTS (SELECT 1 FROM oc_region_fuel WHERE id='aa100000-0000-4000-8000-000000000012');
INSERT INTO oc_region_fuel (id, region_code, product_code, fill_pct, critical_count)
SELECT 'aa100000-0000-4000-8000-000000000013', 'krs', 'АИ-92', 61, 2
WHERE NOT EXISTS (SELECT 1 FROM oc_region_fuel WHERE id='aa100000-0000-4000-8000-000000000013');
INSERT INTO oc_region_fuel (id, region_code, product_code, fill_pct, critical_count)
SELECT 'aa100000-0000-4000-8000-000000000014', 'krs', 'АИ-95', 67, 1
WHERE NOT EXISTS (SELECT 1 FROM oc_region_fuel WHERE id='aa100000-0000-4000-8000-000000000014');
INSERT INTO oc_region_fuel (id, region_code, product_code, fill_pct, critical_count)
SELECT 'aa100000-0000-4000-8000-000000000015', 'krs', 'ДТ', 70, 0
WHERE NOT EXISTS (SELECT 1 FROM oc_region_fuel WHERE id='aa100000-0000-4000-8000-000000000015');
INSERT INTO oc_region_fuel (id, region_code, product_code, fill_pct, critical_count)
SELECT 'aa100000-0000-4000-8000-000000000016', 'krs', 'СУГ', 39, 3
WHERE NOT EXISTS (SELECT 1 FROM oc_region_fuel WHERE id='aa100000-0000-4000-8000-000000000016');

-- station live tanks (005)
INSERT INTO oc_station_tank_live (id, azs_code, tank_no, product_code, level_cm, volume_l, fill_pct, temp_c, density_kg_m3, water_cm, status)
SELECT 'ab100000-0000-4000-8000-000000000001', '005', 1, 'АИ-95', 214.2, 18420, 64, 12.4, 748.2, 0.4, 'норма'
WHERE NOT EXISTS (SELECT 1 FROM oc_station_tank_live WHERE id='ab100000-0000-4000-8000-000000000001');
INSERT INTO oc_station_tank_live (id, azs_code, tank_no, product_code, level_cm, volume_l, fill_pct, temp_c, density_kg_m3, water_cm, status)
SELECT 'ab100000-0000-4000-8000-000000000002', '005', 2, 'АИ-95', 231.8, 22104, 71, 11.9, 748.6, 0.2, 'норма'
WHERE NOT EXISTS (SELECT 1 FROM oc_station_tank_live WHERE id='ab100000-0000-4000-8000-000000000002');
INSERT INTO oc_station_tank_live (id, azs_code, tank_no, product_code, level_cm, volume_l, fill_pct, temp_c, density_kg_m3, water_cm, status)
SELECT 'ab100000-0000-4000-8000-000000000003', '005', 3, 'АИ-92', 188.5, 16200, 58, 12.1, 742.1, 0.6, 'норма'
WHERE NOT EXISTS (SELECT 1 FROM oc_station_tank_live WHERE id='ab100000-0000-4000-8000-000000000003');
INSERT INTO oc_station_tank_live (id, azs_code, tank_no, product_code, level_cm, volume_l, fill_pct, temp_c, density_kg_m3, water_cm, status)
SELECT 'ab100000-0000-4000-8000-000000000004', '005', 4, 'АИ-92', 142.0, 9800, 41, 13.0, 741.4, 0.3, 'внимание'
WHERE NOT EXISTS (SELECT 1 FROM oc_station_tank_live WHERE id='ab100000-0000-4000-8000-000000000004');
INSERT INTO oc_station_tank_live (id, azs_code, tank_no, product_code, level_cm, volume_l, fill_pct, temp_c, density_kg_m3, water_cm, status)
SELECT 'ab100000-0000-4000-8000-000000000005', '005', 5, 'ДТ', 176.3, 15240, 55, 10.8, 835.2, 1.1, 'норма'
WHERE NOT EXISTS (SELECT 1 FROM oc_station_tank_live WHERE id='ab100000-0000-4000-8000-000000000005');
INSERT INTO oc_station_tank_live (id, azs_code, tank_no, product_code, level_cm, volume_l, fill_pct, temp_c, density_kg_m3, water_cm, status)
SELECT 'ab100000-0000-4000-8000-000000000006', '014', 1, 'АИ-92', 256, 18420, 64, 12.4, 742.1, 0.4, 'норма'
WHERE NOT EXISTS (SELECT 1 FROM oc_station_tank_live WHERE id='ab100000-0000-4000-8000-000000000006');
INSERT INTO oc_station_tank_live (id, azs_code, tank_no, product_code, level_cm, volume_l, fill_pct, temp_c, density_kg_m3, water_cm, status)
SELECT 'ab100000-0000-4000-8000-000000000007', '014', 2, 'АИ-95', 284, 22104, 71, 11.8, 748.4, 0.2, 'норма'
WHERE NOT EXISTS (SELECT 1 FROM oc_station_tank_live WHERE id='ab100000-0000-4000-8000-000000000007');

-- pumps 005
INSERT INTO oc_station_pump (id, azs_code, pump_no, product_code, status, total_l, shift_l, rate_lpm, error_pct, last_at)
SELECT v.id, '005', v.n, v.product, v.status, v.total_l, v.shift_l, v.rate_lpm, v.error_pct, NOW() - (v.n || ' hours')::interval
FROM (VALUES
  ('ac100000-0000-4000-8000-000000000001'::uuid, 1, 'АИ-95', 'в работе', 13157::float, 438::float, 18.7::float, 0.09::float),
  ('ac100000-0000-4000-8000-000000000002'::uuid, 2, 'АИ-95', 'в работе', 13474::float, 456::float, 19.4::float, 0.10::float),
  ('ac100000-0000-4000-8000-000000000003'::uuid, 3, 'АИ-92', 'простой', 13791::float, 474::float, 20.1::float, 0.42::float),
  ('ac100000-0000-4000-8000-000000000004'::uuid, 4, 'АИ-92', 'в работе', 14108::float, 492::float, 20.8::float, 0.12::float),
  ('ac100000-0000-4000-8000-000000000005'::uuid, 5, 'АИ-92', 'в работе', 14425::float, 510::float, 21.5::float, 0.13::float),
  ('ac100000-0000-4000-8000-000000000006'::uuid, 6, 'ДТ', 'в работе', 14742::float, 528::float, 22.2::float, 0.14::float),
  ('ac100000-0000-4000-8000-000000000007'::uuid, 7, 'ДТ', 'в работе', 15059::float, 546::float, 22.9::float, 0.15::float),
  ('ac100000-0000-4000-8000-000000000008'::uuid, 8, 'ДТ', 'в работе', 15376::float, 564::float, 23.6::float, 0.16::float)
) AS v(id, n, product, status, total_l, shift_l, rate_lpm, error_pct)
WHERE NOT EXISTS (SELECT 1 FROM oc_station_pump p WHERE p.id = v.id);

-- journals
INSERT INTO oc_station_journal (id, azs_code, journal_type, entry_at, entry_no, description, status)
SELECT 'ad100000-0000-4000-8000-000000000001', '005', 'events', TIMESTAMPTZ '2026-08-03 18:12:00+03', 'EV-01', 'Обнаружена поставка НП · ДТ', 'открыт'
WHERE NOT EXISTS (SELECT 1 FROM oc_station_journal WHERE id='ad100000-0000-4000-8000-000000000001');
INSERT INTO oc_station_journal (id, azs_code, journal_type, entry_at, entry_no, description, status)
SELECT 'ad100000-0000-4000-8000-000000000002', '005', 'events', TIMESTAMPTZ '2026-08-03 12:40:00+03', 'EV-02', 'Нет движения топлива 30 мин · АИ-92', 'выполнен'
WHERE NOT EXISTS (SELECT 1 FROM oc_station_journal WHERE id='ad100000-0000-4000-8000-000000000002');
INSERT INTO oc_station_journal (id, azs_code, journal_type, entry_at, entry_no, description, status)
SELECT 'ad100000-0000-4000-8000-000000000003', '005', 'supplies', TIMESTAMPTZ '2026-08-02 09:15:00+03', 'SUP-11', 'ТТН ЭЮ291847 · 12 144 л АИ-95', 'выполнен'
WHERE NOT EXISTS (SELECT 1 FROM oc_station_journal WHERE id='ad100000-0000-4000-8000-000000000003');
INSERT INTO oc_station_journal (id, azs_code, journal_type, entry_at, entry_no, description, status)
SELECT 'ad100000-0000-4000-8000-000000000004', '005', 'sso', TIMESTAMPTZ '2026-08-03 06:00:00+03', 'SSO-88', 'Смена 1 · остатки сверены', 'выполнен'
WHERE NOT EXISTS (SELECT 1 FROM oc_station_journal WHERE id='ad100000-0000-4000-8000-000000000004');
INSERT INTO oc_station_journal (id, azs_code, journal_type, entry_at, entry_no, description, status)
SELECT 'ad100000-0000-4000-8000-000000000005', '005', 'samples', TIMESTAMPTZ '2026-08-01 11:20:00+03', 'SMP-3', 'Проба РГС-2 · АИ-95', 'открыт'
WHERE NOT EXISTS (SELECT 1 FROM oc_station_journal WHERE id='ad100000-0000-4000-8000-000000000005');

-- discharge
INSERT INTO oc_discharge_session (id, azs_code, ttn_no, status, note)
SELECT 'ae100000-0000-4000-8000-000000000001', '014', 'ЭЮ291847', 'active', 'Слив в процессе'
WHERE NOT EXISTS (SELECT 1 FROM oc_discharge_session WHERE id='ae100000-0000-4000-8000-000000000001');
INSERT INTO oc_discharge_tank (id, session_id, sections, tank_no, product_code, received_kg, plan_kg, timer_label, volume_l, fill_pct, temp_c, density, density_15)
SELECT 'ae100000-0000-4000-8000-000000000011', 'ae100000-0000-4000-8000-000000000001', '14, 12', 1, 'АИ-95', 4210, 12144, '02:44', 5620, 46, 14.2, 748, 752
WHERE NOT EXISTS (SELECT 1 FROM oc_discharge_tank WHERE id='ae100000-0000-4000-8000-000000000011');
INSERT INTO oc_discharge_tank (id, session_id, sections, tank_no, product_code, received_kg, plan_kg, timer_label, volume_l, fill_pct, temp_c, density, density_15)
SELECT 'ae100000-0000-4000-8000-000000000012', 'ae100000-0000-4000-8000-000000000001', '11, 15', 2, 'АИ-92', 3800, 11993, '02:45', 5100, 42, 13.8, 742, 746
WHERE NOT EXISTS (SELECT 1 FROM oc_discharge_tank WHERE id='ae100000-0000-4000-8000-000000000012');

-- stock forecast
INSERT INTO oc_stock_forecast (id, bucket_label, sort_order, fact_pct, forecast_pct, product_code)
SELECT ('af100000-0000-4000-8000-' || lpad(g.i::text, 12, '0'))::uuid,
       lpad(((18 + (g.i/2)) % 24)::text, 2, '0') || ':' || CASE WHEN g.i % 2 = 0 THEN '00' ELSE '30' END,
       g.i,
       GREATEST(20, LEAST(95, ROUND(55 + SIN(g.i / 2.2) * 12))),
       GREATEST(20, LEAST(95, ROUND(55 + SIN(g.i / 2.2) * 12 - g.i * 0.8))),
       'АИ-92'
FROM generate_series(0, 13) AS g(i)
WHERE NOT EXISTS (SELECT 1 FROM oc_stock_forecast LIMIT 1);

-- rgs-trk series
INSERT INTO oc_rgs_trk_series (id, bucket_label, sort_order, rgs_l, trk_l)
SELECT ('a6100000-0000-4000-8000-' || lpad(g.i::text, 12, '0'))::uuid,
       (1 + (g.i / 6))::text || '.12 ' || lpad((6 + g.i)::text, 2, '0') || ':00',
       g.i,
       ROUND((1820 + SIN(g.i / 3.0) * 110 - g.i * 7.5 + CASE WHEN g.i = 14 THEN 40 ELSE 0 END)::numeric, 1),
       ROUND((1800 + SIN(g.i / 3.0) * 120 - g.i * 8)::numeric, 1)
FROM generate_series(0, 23) AS g(i)
WHERE NOT EXISTS (SELECT 1 FROM oc_rgs_trk_series LIMIT 1);

-- balance trend
INSERT INTO oc_balance_trend (id, bucket_label, sort_order, imbalance_l, inflow_l, outflow_l)
SELECT ('a7100000-0000-4000-8000-' || lpad(g.i::text, 12, '0'))::uuid,
       'День ' || (g.i + 1)::text, g.i,
       ROUND((sin(g.i / 2.0) * 180 + (g.i - 3) * 12)::numeric, 1),
       90000 + g.i * 1200, 88000 + g.i * 1100
FROM generate_series(0, 13) AS g(i)
WHERE NOT EXISTS (SELECT 1 FROM oc_balance_trend LIMIT 1);

-- KPI cards
INSERT INTO oc_kpi_card (id, title, total_label, plan_label, segments_json, legend, sort_order)
SELECT 'a8100000-0000-4000-8000-000000000001', 'Поверка СИ', '11 891', '50%',
 '[{"c":"var(--accent)","w":50},{"c":"var(--orange)","w":18},{"c":"var(--red)","w":12}]',
 'Выполнено · ≤30 дн · Просрочено', 1
WHERE NOT EXISTS (SELECT 1 FROM oc_kpi_card WHERE id='a8100000-0000-4000-8000-000000000001');
INSERT INTO oc_kpi_card (id, title, total_label, plan_label, segments_json, legend, sort_order)
SELECT 'a8100000-0000-4000-8000-000000000002', 'Зачистка резервуаров', '0%', '0%',
 '[{"c":"var(--line-strong)","w":100}]', 'План не начат', 2
WHERE NOT EXISTS (SELECT 1 FROM oc_kpi_card WHERE id='a8100000-0000-4000-8000-000000000002');
INSERT INTO oc_kpi_card (id, title, total_label, plan_label, segments_json, legend, sort_order)
SELECT 'a8100000-0000-4000-8000-000000000003', 'Лабораторные испытания', '2 692', '18%',
 '[{"c":"var(--accent)","w":18},{"c":"var(--line-strong)","w":82}]', 'Выполнено за период', 3
WHERE NOT EXISTS (SELECT 1 FROM oc_kpi_card WHERE id='a8100000-0000-4000-8000-000000000003');
INSERT INTO oc_kpi_card (id, title, total_label, plan_label, segments_json, legend, sort_order)
SELECT 'a8100000-0000-4000-8000-000000000004', 'Доступность ИИС', '81%', '81%',
 '[{"c":"var(--green)","w":81},{"c":"var(--orange)","w":8},{"c":"var(--red)","w":6},{"c":"var(--muted)","w":5}]',
 'В работе · Ошибка · Авария · Нет связи', 4
WHERE NOT EXISTS (SELECT 1 FROM oc_kpi_card WHERE id='a8100000-0000-4000-8000-000000000004');
INSERT INTO oc_kpi_card (id, title, total_label, plan_label, segments_json, legend, sort_order)
SELECT 'a8100000-0000-4000-8000-000000000005', 'Погрешность ТРК', '5 513', 'ТРК',
 '[{"c":"var(--green)","w":62},{"c":"var(--yellow)","w":22},{"c":"var(--red)","w":16}]',
 '<0,25% · 0,25–0,5% · >0,5%', 5
WHERE NOT EXISTS (SELECT 1 FROM oc_kpi_card WHERE id='a8100000-0000-4000-8000-000000000005');
INSERT INTO oc_kpi_card (id, title, total_label, plan_label, segments_json, legend, sort_order)
SELECT 'a8100000-0000-4000-8000-000000000006', 'Заявки', '22 578', 'Сроки 33%',
 '[{"c":"var(--accent)","w":45},{"c":"var(--cyan)","w":30},{"c":"var(--red)","w":25}]',
 'Закрыто · Открыто · С нарушением', 6
WHERE NOT EXISTS (SELECT 1 FROM oc_kpi_card WHERE id='a8100000-0000-4000-8000-000000000006');

INSERT INTO oc_kpi_incident_summary (id, title, count_n, azs_count, mass_label, action_label, colors_json, sort_order)
SELECT 'a9100000-0000-4000-8000-000000000001', 'Недовозы', 9, 4, '658 кг', 'Открыть журнал поставок',
 '["#9b7bff","#f5c542","#3ec7f0"]', 1
WHERE NOT EXISTS (SELECT 1 FROM oc_kpi_incident_summary WHERE id='a9100000-0000-4000-8000-000000000001');
INSERT INTO oc_kpi_incident_summary (id, title, count_n, azs_count, mass_label, action_label, colors_json, sort_order)
SELECT 'a9100000-0000-4000-8000-000000000002', 'Отклонение плотности по ТТН', 3, 2, '112 кг', 'Открыть отчёт',
 '["#ff8a3d","#3ec7f0"]', 2
WHERE NOT EXISTS (SELECT 1 FROM oc_kpi_incident_summary WHERE id='a9100000-0000-4000-8000-000000000002');
INSERT INTO oc_kpi_incident_summary (id, title, count_n, azs_count, mass_label, action_label, colors_json, sort_order)
SELECT 'a9100000-0000-4000-8000-000000000003', 'Поставка в РГС без приёмки', 58, 32, '6 612 кг', 'Открыть журнал',
 '["#ff5c6c","#f5c542","#9b7bff"]', 3
WHERE NOT EXISTS (SELECT 1 FROM oc_kpi_incident_summary WHERE id='a9100000-0000-4000-8000-000000000003');

-- calibration profile
INSERT INTO oc_calibration_profile (id, azs_code, tank_no, calibrated_at, ok_levels, total_levels)
SELECT 'aa200000-0000-4000-8000-000000000001', '103', 5, DATE '2026-03-26', 193, 193
WHERE NOT EXISTS (SELECT 1 FROM oc_calibration_profile WHERE id='aa200000-0000-4000-8000-000000000001');

INSERT INTO oc_calibration_profile_row (profile_id, level_cm, viis_l, vtoir_l, dev_l, dev_pct)
SELECT 'aa200000-0000-4000-8000-000000000001', v.level_cm, v.viis, v.vtoir, v.dev_l, v.dev_pct
FROM (VALUES
  (130::float, 430.0::float, 580.5::float, 150.5::float, 35.0::float),
  (300::float, 715.0::float, 972.4::float, 257.4::float, 36.0::float),
  (470::float, 1000.0::float, 1390.0::float, 390.0::float, 39.0::float),
  (640::float, 1285.0::float, 1824.7::float, 539.7::float, 42.0::float),
  (810::float, 1570.0::float, 1695.6::float, 125.6::float, 8.0::float),
  (980::float, 1855.0::float, 1827.2::float, -27.8::float, -1.5::float),
  (1150::float, 2140.0::float, 2311.2::float, 171.2::float, 8.0::float),
  (1320::float, 2425.0::float, 2388.6::float, -36.4::float, -1.5::float),
  (1490::float, 2710.0::float, 2669.4::float, -40.6::float, -1.5::float),
  (1660::float, 2995.0::float, 3234.6::float, 239.6::float, 8.0::float),
  (1830::float, 3280.0::float, 3230.8::float, -49.2::float, -1.5::float),
  (2000::float, 3565.0::float, 3511.5::float, -53.5::float, -1.5::float),
  (2170::float, 3850.0::float, 3792.3::float, -57.7::float, -1.5::float),
  (2340::float, 4135.0::float, 4217.7::float, 82.7::float, 2.0::float),
  (2510::float, 4420.0::float, 4508.4::float, 88.4::float, 2.0::float),
  (2680::float, 4705.0::float, 4799.1::float, 94.1::float, 2.0::float)
) AS v(level_cm, viis, vtoir, dev_l, dev_pct)
WHERE NOT EXISTS (SELECT 1 FROM oc_calibration_profile_row r WHERE r.profile_id='aa200000-0000-4000-8000-000000000001');

-- enrich regions last_at via update stations already present
UPDATE oc_region SET stations_total = stations_total WHERE region_code IS NOT NULL;
