SELECT tank_no, product_code, level_cm, volume_l, fill_pct, temp_c, density_kg_m3, water_cm, status FROM oc_station_tank_live WHERE azs_code = COALESCE(NULLIF(?, ''), '005') ORDER BY tank_no
