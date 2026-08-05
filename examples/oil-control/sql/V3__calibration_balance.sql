INSERT INTO oc_calibration (id, tank_code, version_no, valid_from, valid_to, status, source_name)
SELECT 'dddddddd-dddd-dddd-dddd-dddddddddd01', 'RVS-1', 1, CURRENT_DATE - 30, CURRENT_DATE + 300, 'active', 'seed-linear'
WHERE NOT EXISTS (SELECT 1 FROM oc_calibration WHERE id='dddddddd-dddd-dddd-dddd-dddddddddd01');
INSERT INTO oc_calibration (id, tank_code, version_no, valid_from, valid_to, status, source_name)
SELECT 'dddddddd-dddd-dddd-dddd-dddddddddd02', 'RVS-2', 1, CURRENT_DATE - 30, CURRENT_DATE + 300, 'active', 'seed-linear'
WHERE NOT EXISTS (SELECT 1 FROM oc_calibration WHERE id='dddddddd-dddd-dddd-dddd-dddddddddd02');
INSERT INTO oc_calibration (id, tank_code, version_no, valid_from, valid_to, status, source_name)
SELECT 'dddddddd-dddd-dddd-dddd-dddddddddd03', 'RVS-3', 1, CURRENT_DATE - 30, CURRENT_DATE + 300, 'active', 'seed-linear'
WHERE NOT EXISTS (SELECT 1 FROM oc_calibration WHERE id='dddddddd-dddd-dddd-dddd-dddddddddd03');
INSERT INTO oc_calibration (id, tank_code, version_no, valid_from, valid_to, status, source_name)
SELECT 'dddddddd-dddd-dddd-dddd-dddddddddd04', 'RVS-4', 1, CURRENT_DATE - 30, CURRENT_DATE + 300, 'active', 'seed-linear'
WHERE NOT EXISTS (SELECT 1 FROM oc_calibration WHERE id='dddddddd-dddd-dddd-dddd-dddddddddd04');
INSERT INTO oc_calibration (id, tank_code, version_no, valid_from, valid_to, status, source_name)
SELECT 'dddddddd-dddd-dddd-dddd-dddddddddd05', 'RVS-5', 1, CURRENT_DATE - 30, CURRENT_DATE + 300, 'active', 'seed-linear'
WHERE NOT EXISTS (SELECT 1 FROM oc_calibration WHERE id='dddddddd-dddd-dddd-dddd-dddddddddd05');
INSERT INTO oc_calibration (id, tank_code, version_no, valid_from, valid_to, status, source_name)
SELECT 'dddddddd-dddd-dddd-dddd-dddddddddd06', 'RGS-1A', 1, CURRENT_DATE - 30, CURRENT_DATE + 300, 'active', 'seed-linear'
WHERE NOT EXISTS (SELECT 1 FROM oc_calibration WHERE id='dddddddd-dddd-dddd-dddd-dddddddddd06');
INSERT INTO oc_calibration (id, tank_code, version_no, valid_from, valid_to, status, source_name)
SELECT 'dddddddd-dddd-dddd-dddd-dddddddddd07', 'RGS-2A', 1, CURRENT_DATE - 30, CURRENT_DATE + 300, 'active', 'seed-linear'
WHERE NOT EXISTS (SELECT 1 FROM oc_calibration WHERE id='dddddddd-dddd-dddd-dddd-dddddddddd07');

INSERT INTO oc_calibration_row (calibration_id, level_cm, volume_l)
SELECT c.id, v.level_cm, v.volume_l
FROM oc_calibration c
JOIN (VALUES (0::float,0::float),(600::float,25000::float),(1200::float,50000::float)) AS v(level_cm, volume_l) ON true
WHERE c.tank_code LIKE 'RVS-%'
  AND NOT EXISTS (SELECT 1 FROM oc_calibration_row r WHERE r.calibration_id=c.id AND r.level_cm=v.level_cm);

INSERT INTO oc_calibration_row (calibration_id, level_cm, volume_l)
SELECT c.id, v.level_cm, v.volume_l
FROM oc_calibration c
JOIN (VALUES (0::float,0::float),(200::float,4000::float),(400::float,8000::float)) AS v(level_cm, volume_l) ON true
WHERE c.tank_code LIKE 'RGS-%'
  AND NOT EXISTS (SELECT 1 FROM oc_calibration_row r WHERE r.calibration_id=c.id AND r.level_cm=v.level_cm);

-- simple linear backfill for existing measurements without volume
UPDATE oc_measurement m
SET volume_l = CASE
  WHEN m.tank_code LIKE 'RGS-%' THEN (m.level_cm / 400.0) * 8000.0
  ELSE (m.level_cm / 1200.0) * 50000.0
END
WHERE m.volume_l IS NULL;
