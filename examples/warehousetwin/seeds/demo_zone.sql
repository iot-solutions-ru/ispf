INSERT INTO wt_storage_zone (id, zone_code, aisle_count, status)
SELECT 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'ZONE-A1', 4, 'active'
WHERE NOT EXISTS (SELECT 1 FROM wt_storage_zone WHERE zone_code = 'ZONE-A1');
