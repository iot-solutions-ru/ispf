CREATE TABLE IF NOT EXISTS demo_category (id UUID PRIMARY KEY, category_code VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL);
CREATE TABLE IF NOT EXISTS demo_item (id UUID PRIMARY KEY, item_code VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL, category_id UUID);
CREATE TABLE IF NOT EXISTS demo_metric (metric_key VARCHAR(64) PRIMARY KEY, metric_value BIGINT, status VARCHAR(32));
INSERT INTO demo_category (id, category_code, status) SELECT '11111111-1111-1111-1111-111111111111', 'CAT-A', 'open' WHERE NOT EXISTS (SELECT 1 FROM demo_category WHERE category_code = 'CAT-A');
INSERT INTO demo_item (id, item_code, status, category_id) SELECT '22222222-2222-2222-2222-222222222201', 'ITEM-001', 'ready', '11111111-1111-1111-1111-111111111111' WHERE NOT EXISTS (SELECT 1 FROM demo_item WHERE item_code = 'ITEM-001');
INSERT INTO demo_item (id, item_code, status, category_id) SELECT '22222222-2222-2222-2222-222222222202', 'ITEM-002', 'assigned', '11111111-1111-1111-1111-111111111111' WHERE NOT EXISTS (SELECT 1 FROM demo_item WHERE item_code = 'ITEM-002');
INSERT INTO demo_item (id, item_code, status, category_id) SELECT '22222222-2222-2222-2222-222222222203', 'ITEM-003', 'ready', '11111111-1111-1111-1111-111111111111' WHERE NOT EXISTS (SELECT 1 FROM demo_item WHERE item_code = 'ITEM-003');
INSERT INTO demo_metric (metric_key, metric_value, status) SELECT 'throughput', 42, 'ok' WHERE NOT EXISTS (SELECT 1 FROM demo_metric WHERE metric_key = 'throughput');
