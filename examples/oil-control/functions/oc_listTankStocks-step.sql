SELECT azs_code, address, tank_no, product_code, density_kg_m3, stock_l, fill_pct, free_l, dead_l, measured_at::text AS measured_at FROM oc_tank_stock ORDER BY azs_code, tank_no
