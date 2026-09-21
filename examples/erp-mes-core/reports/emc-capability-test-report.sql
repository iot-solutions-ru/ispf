
SELECT s.spec_id, s.target_kind, s.target_id, s.test_name, COALESCE(s.criterion, '') AS criterion,
       COALESCE(r.measured_value, '') AS measured_value, COALESCE(r.result, '') AS result,
       r.tested_at
FROM emc_capability_test_spec s
LEFT JOIN emc_capability_test_result r ON r.spec_id = s.spec_id
ORDER BY s.spec_id, r.tested_at DESC
