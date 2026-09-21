SELECT azs_code, site_type, tank_no, product_code, detected_at::text AS detected_at, rgs_l, trk_l, abs_dev_l, rel_dev_pct FROM oc_rgs_trk ORDER BY detected_at DESC
