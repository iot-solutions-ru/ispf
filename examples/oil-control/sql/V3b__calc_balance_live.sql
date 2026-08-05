-- reference query used by oc_calcBalance v2

WITH latest AS (
  SELECT DISTINCT ON (tank_code) tank_code, volume_l, measured_at
  FROM oc_measurement
  WHERE volume_l IS NOT NULL
  ORDER BY tank_code, measured_at DESC
),
prev AS (
  SELECT DISTINCT ON (m.tank_code) m.tank_code, m.volume_l, m.measured_at
  FROM oc_measurement m
  WHERE m.volume_l IS NOT NULL
    AND m.measured_at < NOW() - INTERVAL '8 hours'
  ORDER BY m.tank_code, m.measured_at DESC
),
site_stock AS (
  SELECT t.site_code,
    COALESCE(SUM(l.volume_l), 0)::float AS stock_end_l,
    COALESCE(SUM(COALESCE(p.volume_l, l.volume_l)), 0)::float AS stock_start_l
  FROM oc_tank t
  LEFT JOIN latest l ON l.tank_code = t.tank_code
  LEFT JOIN prev p ON p.tank_code = t.tank_code
  WHERE t.active
  GROUP BY t.site_code
),
meter_delta AS (
  SELECT m.zone_code,
    COALESCE(SUM(CASE WHEN m.meter_role = 'inflow' THEN COALESCE(r.delta_l, 0) ELSE 0 END), 0)::float AS inflow_l,
    COALESCE(SUM(CASE WHEN m.meter_role = 'outflow' THEN COALESCE(r.delta_l, 0) ELSE 0 END), 0)::float AS outflow_l
  FROM oc_meter m
  LEFT JOIN oc_meter_reading r ON r.meter_code = m.meter_code AND r.reading_at > NOW() - INTERVAL '24 hours'
  GROUP BY m.zone_code
),
calc AS (
  SELECT
    z.zone_code,
    z.display_name,
    z.loss_norm_pct,
    z.site_code,
    COALESCE(ss.stock_end_l, 0) AS stock_end_l,
    COALESCE(ss.stock_start_l, 0) AS stock_start_l,
    COALESCE(md.inflow_l, 0) AS inflow_l,
    COALESCE(md.outflow_l, 0) AS outflow_l,
    (COALESCE(ss.stock_end_l, 0) - COALESCE(ss.stock_start_l, 0)) AS delta_stock_l
  FROM oc_zone z
  LEFT JOIN site_stock ss ON ss.site_code = z.site_code
  LEFT JOIN meter_delta md ON md.zone_code = z.zone_code
)
SELECT
  zone_code,
  display_name,
  loss_norm_pct,
  ROUND(ABS(
    CASE
      WHEN inflow_l + outflow_l > 0 THEN
        (inflow_l - outflow_l - delta_stock_l) / NULLIF(GREATEST(inflow_l, outflow_l, stock_start_l, 1), 0) * 100
      ELSE
        delta_stock_l / NULLIF(GREATEST(stock_start_l, 1), 0) * 100
    END
  )::numeric, 3)::float AS imbalance_pct,
  ROUND((
    CASE
      WHEN inflow_l + outflow_l > 0 THEN inflow_l - outflow_l - delta_stock_l
      ELSE delta_stock_l
    END
  )::numeric, 1)::float AS imbalance_l,
  ROUND(stock_end_l::numeric, 1)::float AS stock_end_l,
  CASE
    WHEN ABS(
      CASE
        WHEN inflow_l + outflow_l > 0 THEN
          (inflow_l - outflow_l - delta_stock_l) / NULLIF(GREATEST(inflow_l, outflow_l, stock_start_l, 1), 0) * 100
        ELSE delta_stock_l / NULLIF(GREATEST(stock_start_l, 1), 0) * 100
      END
    ) <= loss_norm_pct THEN 'green'
    WHEN ABS(
      CASE
        WHEN inflow_l + outflow_l > 0 THEN
          (inflow_l - outflow_l - delta_stock_l) / NULLIF(GREATEST(inflow_l, outflow_l, stock_start_l, 1), 0) * 100
        ELSE delta_stock_l / NULLIF(GREATEST(stock_start_l, 1), 0) * 100
      END
    ) <= loss_norm_pct + 0.3 THEN 'yellow'
    ELSE 'red'
  END AS status,
  'live: stock Δ8h + meters 24h' AS note
FROM calc
ORDER BY zone_code

