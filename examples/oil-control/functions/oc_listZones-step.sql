SELECT zone_code, display_name, site_code, inflow_desc, outflow_desc, calc_period, loss_norm_pct FROM oc_zone WHERE site_code = 'azs-net' OR zone_code IN ('z_nb_rgs','z_rgs_trk') ORDER BY sort_order
