SELECT batch_id, product_code, origin, volume_l, status, quality_status, received_at::text AS received_at FROM oc_batch ORDER BY received_at DESC
