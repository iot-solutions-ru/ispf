SELECT azs_code, tank_no, product_code, period_label, shifts_label, detected_at::text AS detected_at, delta_l, delta_kg, cause, status FROM oc_imbalance ORDER BY detected_at DESC
