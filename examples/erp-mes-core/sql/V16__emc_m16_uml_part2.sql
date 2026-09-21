CREATE TABLE IF NOT EXISTS emc_hierarchy_scope (
       scope_id VARCHAR(64) PRIMARY KEY,
       name VARCHAR(256) NOT NULL,
       parent_scope_id VARCHAR(64),
       description VARCHAR(512));
CREATE TABLE IF NOT EXISTS emc_equipment_class_property (
       class_id VARCHAR(64) NOT NULL,
       prop_key VARCHAR(64) NOT NULL,
       prop_value VARCHAR(512),
       uom VARCHAR(32),
       PRIMARY KEY (class_id, prop_key));
CREATE TABLE IF NOT EXISTS emc_physical_asset_class_property (
       class_id VARCHAR(64) NOT NULL,
       prop_key VARCHAR(64) NOT NULL,
       prop_value VARCHAR(512),
       uom VARCHAR(32),
       PRIMARY KEY (class_id, prop_key));
CREATE TABLE IF NOT EXISTS emc_personnel_class_property (
       class_id VARCHAR(64) NOT NULL,
       prop_key VARCHAR(64) NOT NULL,
       prop_value VARCHAR(512),
       uom VARCHAR(32),
       PRIMARY KEY (class_id, prop_key));
CREATE TABLE IF NOT EXISTS emc_person_property (
       person_id VARCHAR(64) NOT NULL,
       prop_key VARCHAR(64) NOT NULL,
       prop_value VARCHAR(512),
       uom VARCHAR(32),
       PRIMARY KEY (person_id, prop_key));
CREATE TABLE IF NOT EXISTS emc_material_class_property (
       class_id VARCHAR(64) NOT NULL,
       prop_key VARCHAR(64) NOT NULL,
       prop_value VARCHAR(512),
       uom VARCHAR(32),
       PRIMARY KEY (class_id, prop_key));
CREATE TABLE IF NOT EXISTS emc_material_definition_property (
       definition_id VARCHAR(64) NOT NULL,
       prop_key VARCHAR(64) NOT NULL,
       prop_value VARCHAR(512),
       uom VARCHAR(32),
       PRIMARY KEY (definition_id, prop_key));
CREATE TABLE IF NOT EXISTS emc_qualification_test_spec (
       spec_id VARCHAR(64) PRIMARY KEY,
       person_id VARCHAR(64),
       personnel_class_id VARCHAR(64),
       equipment_id VARCHAR(64),
       equipment_class_id VARCHAR(64),
       test_name VARCHAR(128) NOT NULL,
       criterion VARCHAR(256),
       qualification VARCHAR(128) NOT NULL DEFAULT 'OPERATE');
CREATE TABLE IF NOT EXISTS emc_qualification_test_result (
       result_id UUID PRIMARY KEY,
       spec_id VARCHAR(64) NOT NULL,
       measured_value VARCHAR(128),
       result VARCHAR(32) NOT NULL,
       tested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
       tested_by VARCHAR(64));
CREATE TABLE IF NOT EXISTS emc_material_assembled_from (
       assembly_id VARCHAR(64) PRIMARY KEY,
       parent_definition_id VARCHAR(64) NOT NULL,
       child_definition_id VARCHAR(64),
       child_class_id VARCHAR(64),
       quantity NUMERIC(14,3) NOT NULL DEFAULT 1,
       uom VARCHAR(16),
       assembly_type VARCHAR(32) NOT NULL DEFAULT 'BOM',
       sequence_no INTEGER NOT NULL DEFAULT 1);
CREATE TABLE IF NOT EXISTS emc_segment_parameter_spec (
       spec_id VARCHAR(96) PRIMARY KEY,
       segment_id VARCHAR(64) NOT NULL,
       param_key VARCHAR(64) NOT NULL,
       param_name VARCHAR(128),
       default_value VARCHAR(128),
       uom VARCHAR(32),
       required_flag VARCHAR(8) NOT NULL DEFAULT 'false');
CREATE TABLE IF NOT EXISTS emc_product_segment_material_spec (
       spec_id VARCHAR(96) PRIMARY KEY,
       product_id VARCHAR(64) NOT NULL,
       segment_id VARCHAR(64) NOT NULL,
       material_class_id VARCHAR(64),
       definition_id VARCHAR(64),
       material_use VARCHAR(32) NOT NULL,
       quantity NUMERIC(14,3) NOT NULL DEFAULT 0,
       uom VARCHAR(16));
CREATE TABLE IF NOT EXISTS emc_product_segment_equipment_spec (
       spec_id VARCHAR(96) PRIMARY KEY,
       product_id VARCHAR(64) NOT NULL,
       segment_id VARCHAR(64) NOT NULL,
       equipment_class_id VARCHAR(64),
       equipment_id VARCHAR(64),
       equipment_use VARCHAR(32) NOT NULL DEFAULT 'PRIMARY',
       quantity NUMERIC(14,3) NOT NULL DEFAULT 1);
CREATE TABLE IF NOT EXISTS emc_product_segment_personnel_spec (
       spec_id VARCHAR(96) PRIMARY KEY,
       product_id VARCHAR(64) NOT NULL,
       segment_id VARCHAR(64) NOT NULL,
       personnel_class_id VARCHAR(64),
       person_id VARCHAR(64),
       personnel_use VARCHAR(32) NOT NULL DEFAULT 'OPERATOR',
       quantity NUMERIC(14,3) NOT NULL DEFAULT 1);
CREATE TABLE IF NOT EXISTS emc_product_segment_parameter_spec (
       spec_id VARCHAR(96) PRIMARY KEY,
       product_id VARCHAR(64) NOT NULL,
       segment_id VARCHAR(64) NOT NULL,
       param_key VARCHAR(64) NOT NULL,
       param_name VARCHAR(128),
       default_value VARCHAR(128),
       uom VARCHAR(32),
       required_flag VARCHAR(8) NOT NULL DEFAULT 'false');
CREATE TABLE IF NOT EXISTS emc_ops_capability_equipment (
       capability_id VARCHAR(64) NOT NULL,
       equipment_id VARCHAR(64) NOT NULL DEFAULT '',
       equipment_class_id VARCHAR(64) NOT NULL DEFAULT '',
       quantity NUMERIC(14,3) NOT NULL DEFAULT 1,
       PRIMARY KEY (capability_id, equipment_id, equipment_class_id));
CREATE TABLE IF NOT EXISTS emc_ops_capability_material (
       capability_id VARCHAR(64) NOT NULL,
       definition_id VARCHAR(64) NOT NULL DEFAULT '',
       material_class_id VARCHAR(64) NOT NULL DEFAULT '',
       quantity NUMERIC(14,3) NOT NULL DEFAULT 0,
       uom VARCHAR(16),
       PRIMARY KEY (capability_id, definition_id, material_class_id));
CREATE TABLE IF NOT EXISTS emc_ops_capability_personnel (
       capability_id VARCHAR(64) NOT NULL,
       person_id VARCHAR(64) NOT NULL DEFAULT '',
       personnel_class_id VARCHAR(64) NOT NULL DEFAULT '',
       quantity NUMERIC(14,3) NOT NULL DEFAULT 1,
       PRIMARY KEY (capability_id, person_id, personnel_class_id));
CREATE TABLE IF NOT EXISTS emc_ops_capability_segment (
       capability_id VARCHAR(64) NOT NULL,
       segment_id VARCHAR(64) NOT NULL,
       PRIMARY KEY (capability_id, segment_id));
CREATE TABLE IF NOT EXISTS emc_mom_activity_bff (
       domain VARCHAR(32) NOT NULL,
       activity VARCHAR(32) NOT NULL,
       function_name VARCHAR(128) NOT NULL,
       PRIMARY KEY (domain, activity, function_name));
ALTER TABLE emc_equipment ADD COLUMN IF NOT EXISTS hierarchy_scope_id VARCHAR(64);
ALTER TABLE emc_person ADD COLUMN IF NOT EXISTS hierarchy_scope_id VARCHAR(64);
ALTER TABLE emc_material_definition ADD COLUMN IF NOT EXISTS hierarchy_scope_id VARCHAR(64);
ALTER TABLE emc_operational_location ADD COLUMN IF NOT EXISTS hierarchy_scope_id VARCHAR(64);
INSERT INTO emc_hierarchy_scope (scope_id, name, parent_scope_id, description) SELECT 'SCOPE-DEMO', 'Demo enterprise scope', NULL, 'Default hierarchy scope for demostand' WHERE NOT EXISTS (SELECT 1 FROM emc_hierarchy_scope WHERE scope_id = 'SCOPE-DEMO');
INSERT INTO emc_hierarchy_scope (scope_id, name, parent_scope_id, description) SELECT 'SCOPE-SITE-01', 'Site-01 scope', 'SCOPE-DEMO', 'Site level scope' WHERE NOT EXISTS (SELECT 1 FROM emc_hierarchy_scope WHERE scope_id = 'SCOPE-SITE-01');
UPDATE emc_equipment SET hierarchy_scope_id = 'SCOPE-DEMO' WHERE equipment_id = 'ENT-DEMO' AND hierarchy_scope_id IS NULL;
UPDATE emc_equipment SET hierarchy_scope_id = 'SCOPE-SITE-01' WHERE equipment_id = 'SITE-01' AND hierarchy_scope_id IS NULL;
UPDATE emc_equipment SET hierarchy_scope_id = 'SCOPE-SITE-01'
       WHERE hierarchy_scope_id IS NULL AND equipment_id IN ('AREA-PROD','LINE-A','WU-A01','WU-A02','WH-CENTRAL','WH-LINE-A01');
UPDATE emc_person SET hierarchy_scope_id = 'SCOPE-SITE-01' WHERE hierarchy_scope_id IS NULL;
UPDATE emc_material_definition SET hierarchy_scope_id = 'SCOPE-SITE-01' WHERE hierarchy_scope_id IS NULL;
UPDATE emc_operational_location SET hierarchy_scope_id = 'SCOPE-SITE-01' WHERE hierarchy_scope_id IS NULL;
INSERT INTO emc_equipment_class_property (class_id, prop_key, prop_value, uom) SELECT 'EQC-ASSEMBLY-MACHINE', 'rated_speed', '100', 'pcs/h' WHERE NOT EXISTS (SELECT 1 FROM emc_equipment_class_property WHERE class_id = 'EQC-ASSEMBLY-MACHINE' AND prop_key = 'rated_speed');
INSERT INTO emc_personnel_class_property (class_id, prop_key, prop_value, uom) SELECT 'PCL-OPERATOR', 'shift_pattern', '2x8', NULL WHERE NOT EXISTS (SELECT 1 FROM emc_personnel_class_property WHERE class_id = 'PCL-OPERATOR' AND prop_key = 'shift_pattern');
INSERT INTO emc_person_property (person_id, prop_key, prop_value, uom) SELECT 'EMP-001', 'badge_id', 'B-001', NULL WHERE NOT EXISTS (SELECT 1 FROM emc_person_property WHERE person_id = 'EMP-001' AND prop_key = 'badge_id');
INSERT INTO emc_material_class_property (class_id, prop_key, prop_value, uom) SELECT 'MCL-RAW', 'hazard_class', 'NONE', NULL WHERE NOT EXISTS (SELECT 1 FROM emc_material_class_property WHERE class_id = 'MCL-RAW' AND prop_key = 'hazard_class');
INSERT INTO emc_material_definition_property (definition_id, prop_key, prop_value, uom) SELECT 'RAW-PLASTIC-GRANULE', 'density', '0.92', 'g/cm3' WHERE NOT EXISTS (SELECT 1 FROM emc_material_definition_property WHERE definition_id = 'RAW-PLASTIC-GRANULE' AND prop_key = 'density');
INSERT INTO emc_physical_asset_class_property (class_id, prop_key, prop_value, uom) SELECT 'PAC-MACHINE', 'maintenance_interval_days', '30', 'd' WHERE NOT EXISTS (SELECT 1 FROM emc_physical_asset_class_property WHERE class_id = 'PAC-MACHINE' AND prop_key = 'maintenance_interval_days');
INSERT INTO emc_qualification_test_spec (spec_id, person_id, personnel_class_id, equipment_id, equipment_class_id, test_name, criterion, qualification) SELECT 'QTS-EMP001-A01', 'EMP-001', NULL, 'WU-A01', NULL, 'Operate assembly cell', 'PASS checklist', 'OPERATE' WHERE NOT EXISTS (SELECT 1 FROM emc_qualification_test_spec WHERE spec_id = 'QTS-EMP001-A01');
INSERT INTO emc_qualification_test_result (result_id, spec_id, measured_value, result, tested_by)
       SELECT gen_random_uuid(), 'QTS-EMP001-A01', 'OK', 'PASS', 'EMP-002'
       WHERE NOT EXISTS (SELECT 1 FROM emc_qualification_test_result WHERE spec_id = 'QTS-EMP001-A01');
INSERT INTO emc_material_assembled_from (assembly_id, parent_definition_id, child_definition_id, child_class_id, quantity, uom, assembly_type, sequence_no) SELECT 'AF-FG-GRANULE', 'FG-UNIT-PACKED', 'RAW-PLASTIC-GRANULE', NULL, '2.5', 'kg', 'BOM', '1' WHERE NOT EXISTS (SELECT 1 FROM emc_material_assembled_from WHERE assembly_id = 'AF-FG-GRANULE');
INSERT INTO emc_material_assembled_from (assembly_id, parent_definition_id, child_definition_id, child_class_id, quantity, uom, assembly_type, sequence_no) SELECT 'AF-FG-BOX', 'FG-UNIT-PACKED', 'RAW-PACKAGING-BOX', NULL, '1', 'pcs', 'BOM', '2' WHERE NOT EXISTS (SELECT 1 FROM emc_material_assembled_from WHERE assembly_id = 'AF-FG-BOX');
INSERT INTO emc_segment_parameter_spec (spec_id, segment_id, param_key, param_name, default_value, uom, required_flag) SELECT 'SEG-ASSEMBLE:TEMP', 'SEG-ASSEMBLE', 'TEMPERATURE', 'Process temperature', '210', 'C', 'true' WHERE NOT EXISTS (SELECT 1 FROM emc_segment_parameter_spec WHERE spec_id = 'SEG-ASSEMBLE:TEMP');
INSERT INTO emc_product_segment_material_spec (spec_id, product_id, segment_id, material_class_id, definition_id, material_use, quantity, uom) SELECT 'PD-UNIT:SEG-PACK:OUT', 'PD-UNIT-PACKED', 'SEG-PACK', NULL, 'FG-UNIT-PACKED', 'PRODUCED', '1', 'pcs' WHERE NOT EXISTS (SELECT 1 FROM emc_product_segment_material_spec WHERE spec_id = 'PD-UNIT:SEG-PACK:OUT');
INSERT INTO emc_product_segment_equipment_spec (spec_id, product_id, segment_id, equipment_class_id, equipment_id, equipment_use, quantity) SELECT 'PD-UNIT:SEG-PACK:EQ', 'PD-UNIT-PACKED', 'SEG-PACK', 'EQC-PACK-MACHINE', '', 'PRIMARY', '1' WHERE NOT EXISTS (SELECT 1 FROM emc_product_segment_equipment_spec WHERE spec_id = 'PD-UNIT:SEG-PACK:EQ');
INSERT INTO emc_product_segment_personnel_spec (spec_id, product_id, segment_id, personnel_class_id, person_id, personnel_use, quantity) SELECT 'PD-UNIT:SEG-PACK:PERS', 'PD-UNIT-PACKED', 'SEG-PACK', 'PCL-OPERATOR', '', 'OPERATOR', '1' WHERE NOT EXISTS (SELECT 1 FROM emc_product_segment_personnel_spec WHERE spec_id = 'PD-UNIT:SEG-PACK:PERS');
INSERT INTO emc_product_segment_parameter_spec (spec_id, product_id, segment_id, param_key, param_name, default_value, uom, required_flag) SELECT 'PD-UNIT:SEG-PACK:RATE', 'PD-UNIT-PACKED', 'SEG-PACK', 'PACK_RATE', 'Pack rate', '60', 'pcs/h', 'false' WHERE NOT EXISTS (SELECT 1 FROM emc_product_segment_parameter_spec WHERE spec_id = 'PD-UNIT:SEG-PACK:RATE');
INSERT INTO emc_ops_capability_equipment (capability_id, equipment_id, equipment_class_id, quantity) SELECT 'CAP-WU-A01-ASSEMBLE', 'WU-A01', 'EQC-ASSEMBLY-MACHINE', '1' WHERE NOT EXISTS (SELECT 1 FROM emc_ops_capability_equipment WHERE capability_id = 'CAP-WU-A01-ASSEMBLE' AND equipment_id = 'WU-A01');
INSERT INTO emc_ops_capability_material (capability_id, definition_id, material_class_id, quantity, uom) SELECT 'CAP-WU-A01-ASSEMBLE', 'RAW-PLASTIC-GRANULE', 'MCL-RAW', '100', 'kg' WHERE NOT EXISTS (SELECT 1 FROM emc_ops_capability_material WHERE capability_id = 'CAP-WU-A01-ASSEMBLE' AND definition_id = 'RAW-PLASTIC-GRANULE');
INSERT INTO emc_ops_capability_personnel (capability_id, person_id, personnel_class_id, quantity) SELECT 'CAP-WU-A01-ASSEMBLE', '', 'PCL-OPERATOR', '1' WHERE NOT EXISTS (SELECT 1 FROM emc_ops_capability_personnel WHERE capability_id = 'CAP-WU-A01-ASSEMBLE' AND personnel_class_id = 'PCL-OPERATOR');
INSERT INTO emc_ops_capability_segment (capability_id, segment_id) SELECT 'CAP-WU-A01-ASSEMBLE', 'SEG-ASSEMBLE' WHERE NOT EXISTS (SELECT 1 FROM emc_ops_capability_segment WHERE capability_id = 'CAP-WU-A01-ASSEMBLE' AND segment_id = 'SEG-ASSEMBLE');
INSERT INTO emc_mom_activity_bff (domain, activity, function_name) SELECT 'PRODUCTION', 'DEFINITION', 'emc_segment_list' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity_bff WHERE domain = 'PRODUCTION' AND activity = 'DEFINITION' AND function_name = 'emc_segment_list');
INSERT INTO emc_mom_activity_bff (domain, activity, function_name) SELECT 'PRODUCTION', 'RESOURCE', 'emc_equipment_list' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity_bff WHERE domain = 'PRODUCTION' AND activity = 'RESOURCE' AND function_name = 'emc_equipment_list');
INSERT INTO emc_mom_activity_bff (domain, activity, function_name) SELECT 'PRODUCTION', 'DETAILED_SCHEDULING', 'emc_schedule_receive' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity_bff WHERE domain = 'PRODUCTION' AND activity = 'DETAILED_SCHEDULING' AND function_name = 'emc_schedule_receive');
INSERT INTO emc_mom_activity_bff (domain, activity, function_name) SELECT 'PRODUCTION', 'DISPATCHING', 'emc_joborder_release' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity_bff WHERE domain = 'PRODUCTION' AND activity = 'DISPATCHING' AND function_name = 'emc_joborder_release');
INSERT INTO emc_mom_activity_bff (domain, activity, function_name) SELECT 'PRODUCTION', 'EXECUTION', 'emc_joborder_start' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity_bff WHERE domain = 'PRODUCTION' AND activity = 'EXECUTION' AND function_name = 'emc_joborder_start');
INSERT INTO emc_mom_activity_bff (domain, activity, function_name) SELECT 'PRODUCTION', 'DATA_COLLECTION', 'emc_dc_recordQuantity' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity_bff WHERE domain = 'PRODUCTION' AND activity = 'DATA_COLLECTION' AND function_name = 'emc_dc_recordQuantity');
INSERT INTO emc_mom_activity_bff (domain, activity, function_name) SELECT 'PRODUCTION', 'TRACKING', 'emc_track_genealogyTreeByLot' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity_bff WHERE domain = 'PRODUCTION' AND activity = 'TRACKING' AND function_name = 'emc_track_genealogyTreeByLot');
INSERT INTO emc_mom_activity_bff (domain, activity, function_name) SELECT 'PRODUCTION', 'PERFORMANCE_ANALYSIS', 'emc_oee_calcShift' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity_bff WHERE domain = 'PRODUCTION' AND activity = 'PERFORMANCE_ANALYSIS' AND function_name = 'emc_oee_calcShift');
INSERT INTO emc_mom_activity_bff (domain, activity, function_name) SELECT 'QUALITY', 'DEFINITION', 'emc_qa_listDefects' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity_bff WHERE domain = 'QUALITY' AND activity = 'DEFINITION' AND function_name = 'emc_qa_listDefects');
INSERT INTO emc_mom_activity_bff (domain, activity, function_name) SELECT 'QUALITY', 'RESOURCE', 'emc_person_list' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity_bff WHERE domain = 'QUALITY' AND activity = 'RESOURCE' AND function_name = 'emc_person_list');
INSERT INTO emc_mom_activity_bff (domain, activity, function_name) SELECT 'QUALITY', 'DETAILED_SCHEDULING', 'emc_domainschedule_list' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity_bff WHERE domain = 'QUALITY' AND activity = 'DETAILED_SCHEDULING' AND function_name = 'emc_domainschedule_list');
INSERT INTO emc_mom_activity_bff (domain, activity, function_name) SELECT 'QUALITY', 'DISPATCHING', 'emc_qa_registerDefect' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity_bff WHERE domain = 'QUALITY' AND activity = 'DISPATCHING' AND function_name = 'emc_qa_registerDefect');
INSERT INTO emc_mom_activity_bff (domain, activity, function_name) SELECT 'QUALITY', 'EXECUTION', 'emc_qa_confirmDefect' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity_bff WHERE domain = 'QUALITY' AND activity = 'EXECUTION' AND function_name = 'emc_qa_confirmDefect');
INSERT INTO emc_mom_activity_bff (domain, activity, function_name) SELECT 'QUALITY', 'DATA_COLLECTION', 'emc_qa_recordTestResult' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity_bff WHERE domain = 'QUALITY' AND activity = 'DATA_COLLECTION' AND function_name = 'emc_qa_recordTestResult');
INSERT INTO emc_mom_activity_bff (domain, activity, function_name) SELECT 'QUALITY', 'TRACKING', 'emc_qa_listDefects' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity_bff WHERE domain = 'QUALITY' AND activity = 'TRACKING' AND function_name = 'emc_qa_listDefects');
INSERT INTO emc_mom_activity_bff (domain, activity, function_name) SELECT 'QUALITY', 'PERFORMANCE_ANALYSIS', 'emc_qa_defectRateKpi' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity_bff WHERE domain = 'QUALITY' AND activity = 'PERFORMANCE_ANALYSIS' AND function_name = 'emc_qa_defectRateKpi');
INSERT INTO emc_mom_activity_bff (domain, activity, function_name) SELECT 'INVENTORY', 'DEFINITION', 'emc_stock_list' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity_bff WHERE domain = 'INVENTORY' AND activity = 'DEFINITION' AND function_name = 'emc_stock_list');
INSERT INTO emc_mom_activity_bff (domain, activity, function_name) SELECT 'INVENTORY', 'RESOURCE', 'emc_location_list' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity_bff WHERE domain = 'INVENTORY' AND activity = 'RESOURCE' AND function_name = 'emc_location_list');
INSERT INTO emc_mom_activity_bff (domain, activity, function_name) SELECT 'INVENTORY', 'DETAILED_SCHEDULING', 'emc_domainschedule_list' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity_bff WHERE domain = 'INVENTORY' AND activity = 'DETAILED_SCHEDULING' AND function_name = 'emc_domainschedule_list');
INSERT INTO emc_mom_activity_bff (domain, activity, function_name) SELECT 'INVENTORY', 'DISPATCHING', 'emc_invdoc_create' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity_bff WHERE domain = 'INVENTORY' AND activity = 'DISPATCHING' AND function_name = 'emc_invdoc_create');
INSERT INTO emc_mom_activity_bff (domain, activity, function_name) SELECT 'INVENTORY', 'EXECUTION', 'emc_invdoc_apply' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity_bff WHERE domain = 'INVENTORY' AND activity = 'EXECUTION' AND function_name = 'emc_invdoc_apply');
INSERT INTO emc_mom_activity_bff (domain, activity, function_name) SELECT 'INVENTORY', 'DATA_COLLECTION', 'emc_stock_list' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity_bff WHERE domain = 'INVENTORY' AND activity = 'DATA_COLLECTION' AND function_name = 'emc_stock_list');
INSERT INTO emc_mom_activity_bff (domain, activity, function_name) SELECT 'INVENTORY', 'TRACKING', 'emc_stock_list' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity_bff WHERE domain = 'INVENTORY' AND activity = 'TRACKING' AND function_name = 'emc_stock_list');
INSERT INTO emc_mom_activity_bff (domain, activity, function_name) SELECT 'INVENTORY', 'PERFORMANCE_ANALYSIS', 'emc_inv_turnsKpi' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity_bff WHERE domain = 'INVENTORY' AND activity = 'PERFORMANCE_ANALYSIS' AND function_name = 'emc_inv_turnsKpi');
INSERT INTO emc_mom_activity_bff (domain, activity, function_name) SELECT 'MAINTENANCE', 'DEFINITION', 'emc_maint_list' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity_bff WHERE domain = 'MAINTENANCE' AND activity = 'DEFINITION' AND function_name = 'emc_maint_list');
INSERT INTO emc_mom_activity_bff (domain, activity, function_name) SELECT 'MAINTENANCE', 'RESOURCE', 'emc_equipment_list' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity_bff WHERE domain = 'MAINTENANCE' AND activity = 'RESOURCE' AND function_name = 'emc_equipment_list');
INSERT INTO emc_mom_activity_bff (domain, activity, function_name) SELECT 'MAINTENANCE', 'DETAILED_SCHEDULING', 'emc_domainschedule_list' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity_bff WHERE domain = 'MAINTENANCE' AND activity = 'DETAILED_SCHEDULING' AND function_name = 'emc_domainschedule_list');
INSERT INTO emc_mom_activity_bff (domain, activity, function_name) SELECT 'MAINTENANCE', 'DISPATCHING', 'emc_maint_acceptRequest' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity_bff WHERE domain = 'MAINTENANCE' AND activity = 'DISPATCHING' AND function_name = 'emc_maint_acceptRequest');
INSERT INTO emc_mom_activity_bff (domain, activity, function_name) SELECT 'MAINTENANCE', 'EXECUTION', 'emc_maint_completeWorkOrder' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity_bff WHERE domain = 'MAINTENANCE' AND activity = 'EXECUTION' AND function_name = 'emc_maint_completeWorkOrder');
INSERT INTO emc_mom_activity_bff (domain, activity, function_name) SELECT 'MAINTENANCE', 'DATA_COLLECTION', 'emc_event_register' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity_bff WHERE domain = 'MAINTENANCE' AND activity = 'DATA_COLLECTION' AND function_name = 'emc_event_register');
INSERT INTO emc_mom_activity_bff (domain, activity, function_name) SELECT 'MAINTENANCE', 'TRACKING', 'emc_maint_list' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity_bff WHERE domain = 'MAINTENANCE' AND activity = 'TRACKING' AND function_name = 'emc_maint_list');
INSERT INTO emc_mom_activity_bff (domain, activity, function_name) SELECT 'MAINTENANCE', 'PERFORMANCE_ANALYSIS', 'emc_maint_mttrMtbf' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity_bff WHERE domain = 'MAINTENANCE' AND activity = 'PERFORMANCE_ANALYSIS' AND function_name = 'emc_maint_mttrMtbf')
