CREATE TABLE IF NOT EXISTS emc_physical_asset_class (
       class_id VARCHAR(64) PRIMARY KEY,
       description VARCHAR(256),
       parent_class_id VARCHAR(64));
CREATE TABLE IF NOT EXISTS emc_physical_asset (
       asset_id VARCHAR(64) PRIMARY KEY,
       class_id VARCHAR(64),
       equipment_id VARCHAR(64),
       serial_no VARCHAR(128),
       manufacturer VARCHAR(128),
       description VARCHAR(256),
       status VARCHAR(32) NOT NULL DEFAULT 'IN_SERVICE');
CREATE TABLE IF NOT EXISTS emc_physical_asset_property (
       asset_id VARCHAR(64) NOT NULL,
       prop_key VARCHAR(64) NOT NULL,
       prop_value VARCHAR(512),
       uom VARCHAR(32),
       PRIMARY KEY (asset_id, prop_key));
CREATE TABLE IF NOT EXISTS emc_product_definition (
       product_id VARCHAR(64) PRIMARY KEY,
       description VARCHAR(256),
       fg_definition_id VARCHAR(64),
       status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE');
CREATE TABLE IF NOT EXISTS emc_product_segment (
       product_id VARCHAR(64) NOT NULL,
       segment_id VARCHAR(64) NOT NULL,
       sequence_no INTEGER NOT NULL DEFAULT 1,
       PRIMARY KEY (product_id, segment_id));
CREATE TABLE IF NOT EXISTS emc_capability_test_spec (
       spec_id VARCHAR(64) PRIMARY KEY,
       target_kind VARCHAR(32) NOT NULL,
       target_id VARCHAR(64) NOT NULL,
       test_name VARCHAR(128) NOT NULL,
       criterion VARCHAR(256),
       uom VARCHAR(16));
CREATE TABLE IF NOT EXISTS emc_capability_test_result (
       result_id UUID PRIMARY KEY,
       spec_id VARCHAR(64) NOT NULL,
       measured_value VARCHAR(128),
       result VARCHAR(32) NOT NULL,
       tested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
       tested_by VARCHAR(64));
CREATE TABLE IF NOT EXISTS emc_operations_capability (
       capability_id VARCHAR(64) PRIMARY KEY,
       operations_type VARCHAR(32) NOT NULL DEFAULT 'PRODUCTION',
       equipment_id VARCHAR(64),
       segment_id VARCHAR(64),
       reason VARCHAR(256),
       available_from TIMESTAMP,
       available_to TIMESTAMP,
       status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE');
CREATE TABLE IF NOT EXISTS emc_operations_performance (
       performance_id VARCHAR(64) PRIMARY KEY,
       operations_type VARCHAR(32) NOT NULL DEFAULT 'PRODUCTION',
       equipment_id VARCHAR(64),
       shift_id VARCHAR(64),
       good_qty NUMERIC(14,3) NOT NULL DEFAULT 0,
       reject_qty NUMERIC(14,3) NOT NULL DEFAULT 0,
       run_min NUMERIC(14,3) NOT NULL DEFAULT 0,
       downtime_min NUMERIC(14,3) NOT NULL DEFAULT 0,
       note VARCHAR(256),
       calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS emc_mom_activity (
       domain VARCHAR(32) NOT NULL,
       activity VARCHAR(32) NOT NULL,
       status VARCHAR(16) NOT NULL,
       note VARCHAR(256),
       ui_link VARCHAR(64),
       PRIMARY KEY (domain, activity));
INSERT INTO emc_physical_asset_class (class_id, description, parent_class_id) SELECT 'PAC-MACHINE', 'Production machines', NULL WHERE NOT EXISTS (SELECT 1 FROM emc_physical_asset_class WHERE class_id = 'PAC-MACHINE');
INSERT INTO emc_physical_asset (asset_id, class_id, equipment_id, serial_no, manufacturer, description, status) SELECT 'AST-A01', 'PAC-MACHINE', 'WU-A01', 'SN-A01-001', 'DemoOEM', 'Assembly cell asset', 'IN_SERVICE' WHERE NOT EXISTS (SELECT 1 FROM emc_physical_asset WHERE asset_id = 'AST-A01');
INSERT INTO emc_physical_asset (asset_id, class_id, equipment_id, serial_no, manufacturer, description, status) SELECT 'AST-A02', 'PAC-MACHINE', 'WU-A02', 'SN-A02-001', 'DemoOEM', 'Packing cell asset', 'IN_SERVICE' WHERE NOT EXISTS (SELECT 1 FROM emc_physical_asset WHERE asset_id = 'AST-A02');
INSERT INTO emc_product_definition (product_id, description, fg_definition_id, status) SELECT 'PD-UNIT-PACKED', 'Packed finished unit', 'FG-UNIT-PACKED', 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM emc_product_definition WHERE product_id = 'PD-UNIT-PACKED');
INSERT INTO emc_product_segment (product_id, segment_id, sequence_no) SELECT 'PD-UNIT-PACKED', 'SEG-ASSEMBLE', '1' WHERE NOT EXISTS (SELECT 1 FROM emc_product_segment WHERE product_id = 'PD-UNIT-PACKED' AND segment_id = 'SEG-ASSEMBLE');
INSERT INTO emc_product_segment (product_id, segment_id, sequence_no) SELECT 'PD-UNIT-PACKED', 'SEG-PACK', '2' WHERE NOT EXISTS (SELECT 1 FROM emc_product_segment WHERE product_id = 'PD-UNIT-PACKED' AND segment_id = 'SEG-PACK');
INSERT INTO emc_capability_test_spec (spec_id, target_kind, target_id, test_name, criterion, uom) SELECT 'CTS-WU-A01-SPEED', 'EQUIPMENT', 'WU-A01', 'Rated speed check', '>= 80', 'pcs/h' WHERE NOT EXISTS (SELECT 1 FROM emc_capability_test_spec WHERE spec_id = 'CTS-WU-A01-SPEED');
INSERT INTO emc_capability_test_result (result_id, spec_id, measured_value, result, tested_by)
       SELECT gen_random_uuid(), 'CTS-WU-A01-SPEED', '95', 'PASS', 'EMP-001'
       WHERE NOT EXISTS (SELECT 1 FROM emc_capability_test_result WHERE spec_id = 'CTS-WU-A01-SPEED');
INSERT INTO emc_operations_capability (capability_id, operations_type, equipment_id, segment_id, reason, status) SELECT 'CAP-WU-A01-ASSEMBLE', 'PRODUCTION', 'WU-A01', 'SEG-ASSEMBLE', 'Qualified + capability test PASS', 'AVAILABLE' WHERE NOT EXISTS (SELECT 1 FROM emc_operations_capability WHERE capability_id = 'CAP-WU-A01-ASSEMBLE');
INSERT INTO emc_operations_capability (capability_id, operations_type, equipment_id, segment_id, reason, status) SELECT 'CAP-WU-A02-PACK', 'PRODUCTION', 'WU-A02', 'SEG-PACK', 'Qualified packing cell', 'AVAILABLE' WHERE NOT EXISTS (SELECT 1 FROM emc_operations_capability WHERE capability_id = 'CAP-WU-A02-PACK');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'PRODUCTION', 'DEFINITION', 'COVERED', 'Process segment + work master', 'emc-dispatch' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'PRODUCTION' AND activity = 'DEFINITION');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'PRODUCTION', 'RESOURCE', 'COVERED', 'Equipment / personnel / material', 'emc-inventory' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'PRODUCTION' AND activity = 'RESOURCE');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'PRODUCTION', 'DETAILED_SCHEDULING', 'COVERED', 'Work schedule receive + domain schedule', 'emc-dispatch' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'PRODUCTION' AND activity = 'DETAILED_SCHEDULING');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'PRODUCTION', 'DISPATCHING', 'COVERED', 'Release / start / pause / resume', 'emc-dispatch' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'PRODUCTION' AND activity = 'DISPATCHING');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'PRODUCTION', 'EXECUTION', 'COVERED', 'Job order lifecycle', 'emc-execution' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'PRODUCTION' AND activity = 'EXECUTION');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'PRODUCTION', 'DATA_COLLECTION', 'COVERED', 'PDC + material actuals', 'emc-execution' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'PRODUCTION' AND activity = 'DATA_COLLECTION');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'PRODUCTION', 'TRACKING', 'COVERED', 'Lot genealogy tree', 'emc-genealogy' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'PRODUCTION' AND activity = 'TRACKING');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'PRODUCTION', 'PERFORMANCE_ANALYSIS', 'COVERED', 'OEE A×P×Q', 'emc-oee' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'PRODUCTION' AND activity = 'PERFORMANCE_ANALYSIS');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'QUALITY', 'DEFINITION', 'COVERED', 'Defect types / reason codes', 'emc-quality' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'QUALITY' AND activity = 'DEFINITION');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'QUALITY', 'RESOURCE', 'COVERED', 'QA personnel via person catalog', 'emc-quality' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'QUALITY' AND activity = 'RESOURCE');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'QUALITY', 'DETAILED_SCHEDULING', 'COVERED', 'QA sample plan (domain schedule)', 'emc-quality' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'QUALITY' AND activity = 'DETAILED_SCHEDULING');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'QUALITY', 'DISPATCHING', 'COVERED', 'Defect workflow dispatch', 'emc-quality' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'QUALITY' AND activity = 'DISPATCHING');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'QUALITY', 'EXECUTION', 'COVERED', 'Confirm / reject / close defect', 'emc-quality' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'QUALITY' AND activity = 'EXECUTION');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'QUALITY', 'DATA_COLLECTION', 'COVERED', 'QA test results', 'emc-quality' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'QUALITY' AND activity = 'DATA_COLLECTION');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'QUALITY', 'TRACKING', 'COVERED', 'Defect status history', 'emc-quality' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'QUALITY' AND activity = 'TRACKING');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'QUALITY', 'PERFORMANCE_ANALYSIS', 'COVERED', 'Defect rate KPI', 'emc-mom-matrix' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'QUALITY' AND activity = 'PERFORMANCE_ANALYSIS');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'INVENTORY', 'DEFINITION', 'COVERED', 'Inventory document kinds', 'emc-inventory' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'INVENTORY' AND activity = 'DEFINITION');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'INVENTORY', 'RESOURCE', 'COVERED', 'Storage zones + operational locations', 'emc-inventory' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'INVENTORY' AND activity = 'RESOURCE');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'INVENTORY', 'DETAILED_SCHEDULING', 'COVERED', 'Replenishment schedule', 'emc-inventory' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'INVENTORY' AND activity = 'DETAILED_SCHEDULING');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'INVENTORY', 'DISPATCHING', 'COVERED', 'Submit inventory document', 'emc-inventory' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'INVENTORY' AND activity = 'DISPATCHING');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'INVENTORY', 'EXECUTION', 'COVERED', 'Apply inventory document', 'emc-inventory' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'INVENTORY' AND activity = 'EXECUTION');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'INVENTORY', 'DATA_COLLECTION', 'COVERED', 'Document lines / stock qty', 'emc-inventory' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'INVENTORY' AND activity = 'DATA_COLLECTION');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'INVENTORY', 'TRACKING', 'COVERED', 'Stock + material movement', 'emc-inventory' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'INVENTORY' AND activity = 'TRACKING');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'INVENTORY', 'PERFORMANCE_ANALYSIS', 'COVERED', 'Inventory turns KPI', 'emc-mom-matrix' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'INVENTORY' AND activity = 'PERFORMANCE_ANALYSIS');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'MAINTENANCE', 'DEFINITION', 'COVERED', 'Maintenance request model', 'emc-oee' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'MAINTENANCE' AND activity = 'DEFINITION');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'MAINTENANCE', 'RESOURCE', 'COVERED', 'Equipment as maint target', 'emc-oee' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'MAINTENANCE' AND activity = 'RESOURCE');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'MAINTENANCE', 'DETAILED_SCHEDULING', 'COVERED', 'PM calendar (domain schedule)', 'emc-oee' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'MAINTENANCE' AND activity = 'DETAILED_SCHEDULING');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'MAINTENANCE', 'DISPATCHING', 'COVERED', 'Accept maintenance request', 'emc-oee' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'MAINTENANCE' AND activity = 'DISPATCHING');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'MAINTENANCE', 'EXECUTION', 'COVERED', 'Complete work order', 'emc-oee' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'MAINTENANCE' AND activity = 'EXECUTION');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'MAINTENANCE', 'DATA_COLLECTION', 'COVERED', 'Event / downtime capture', 'emc-oee' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'MAINTENANCE' AND activity = 'DATA_COLLECTION');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'MAINTENANCE', 'TRACKING', 'COVERED', 'Maintenance list', 'emc-oee' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'MAINTENANCE' AND activity = 'TRACKING');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'MAINTENANCE', 'PERFORMANCE_ANALYSIS', 'COVERED', 'MTTR / MTBF', 'emc-mom-matrix' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'MAINTENANCE' AND activity = 'PERFORMANCE_ANALYSIS')
