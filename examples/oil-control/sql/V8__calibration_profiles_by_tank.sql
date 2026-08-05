-- Профили РГС по резервуарам (геометрия/данные различаются)
INSERT INTO oc_calibration_profile (id, azs_code, tank_no, calibrated_at, ok_levels, total_levels)
SELECT 'aa200000-0000-4000-8000-000000000014', '014', 1, DATE '2026-02-12', 178, 186
WHERE NOT EXISTS (SELECT 1 FROM oc_calibration_profile WHERE id='aa200000-0000-4000-8000-000000000014');
INSERT INTO oc_calibration_profile (id, azs_code, tank_no, calibrated_at, ok_levels, total_levels)
SELECT 'aa200000-0000-4000-8000-000000000015', '014', 2, DATE '2026-01-20', 165, 180
WHERE NOT EXISTS (SELECT 1 FROM oc_calibration_profile WHERE id='aa200000-0000-4000-8000-000000000015');
INSERT INTO oc_calibration_profile (id, azs_code, tank_no, calibrated_at, ok_levels, total_levels)
SELECT 'aa200000-0000-4000-8000-000000000016', '005', 1, DATE '2025-11-08', 171, 184
WHERE NOT EXISTS (SELECT 1 FROM oc_calibration_profile WHERE id='aa200000-0000-4000-8000-000000000016');
INSERT INTO oc_calibration_profile (id, azs_code, tank_no, calibrated_at, ok_levels, total_levels)
SELECT 'aa200000-0000-4000-8000-000000000017', '005', 3, DATE '2026-04-03', 155, 172
WHERE NOT EXISTS (SELECT 1 FROM oc_calibration_profile WHERE id='aa200000-0000-4000-8000-000000000017');
INSERT INTO oc_calibration_profile (id, azs_code, tank_no, calibrated_at, ok_levels, total_levels)
SELECT 'aa200000-0000-4000-8000-000000000018', '870', 1, DATE '2026-03-01', 188, 192
WHERE NOT EXISTS (SELECT 1 FROM oc_calibration_profile WHERE id='aa200000-0000-4000-8000-000000000018');

-- rows: seed from base template with tank-specific multipliers via generate_series
INSERT INTO oc_calibration_profile_row (profile_id, level_cm, viis_l, vtoir_l, dev_l, dev_pct)
SELECT p.id,
  110 + g.i * 160 + (p.tank_no * 8),
  350 + g.i * 240 + (p.tank_no * 40),
  (350 + g.i * 240 + (p.tank_no * 40)) * (1 + 0.05 * sin(g.i * 0.7 + p.tank_no) + CASE WHEN g.i < 3 THEN 0.15 ELSE 0 END),
  ((350 + g.i * 240 + (p.tank_no * 40)) * (1 + 0.05 * sin(g.i * 0.7 + p.tank_no) + CASE WHEN g.i < 3 THEN 0.15 ELSE 0 END))
    - (350 + g.i * 240 + (p.tank_no * 40)),
  100 * (
    ((350 + g.i * 240 + (p.tank_no * 40)) * (1 + 0.05 * sin(g.i * 0.7 + p.tank_no) + CASE WHEN g.i < 3 THEN 0.15 ELSE 0 END))
      / NULLIF(350 + g.i * 240 + (p.tank_no * 40), 0) - 1
  )
FROM oc_calibration_profile p
CROSS JOIN generate_series(0, 15) AS g(i)
WHERE p.id IN (
  'aa200000-0000-4000-8000-000000000014',
  'aa200000-0000-4000-8000-000000000015',
  'aa200000-0000-4000-8000-000000000016',
  'aa200000-0000-4000-8000-000000000017',
  'aa200000-0000-4000-8000-000000000018'
)
AND NOT EXISTS (SELECT 1 FROM oc_calibration_profile_row r WHERE r.profile_id = p.id);
