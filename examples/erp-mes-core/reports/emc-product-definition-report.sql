
SELECT p.product_id, COALESCE(p.description, '') AS description,
       COALESCE(p.fg_definition_id, '') AS fg_definition_id, p.status,
       (SELECT COUNT(*) FROM emc_product_segment ps WHERE ps.product_id = p.product_id) AS segments
FROM emc_product_definition p ORDER BY p.product_id
