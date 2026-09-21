CREATE TABLE IF NOT EXISTS emc_container_class (
       class_id VARCHAR(64) PRIMARY KEY,
       name VARCHAR(256) NOT NULL,
       description VARCHAR(512),
       capacity_uom VARCHAR(32));
CREATE TABLE IF NOT EXISTS emc_container (
       container_id VARCHAR(64) PRIMARY KEY,
       class_id VARCHAR(64) NOT NULL,
       name VARCHAR(256) NOT NULL,
       description VARCHAR(512),
       hierarchy_scope_id VARCHAR(64),
       capacity NUMERIC(14,3),
       capacity_uom VARCHAR(32),
       status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE');
CREATE TABLE IF NOT EXISTS emc_container_property (
       container_id VARCHAR(64) NOT NULL,
       prop_key VARCHAR(64) NOT NULL,
       prop_value VARCHAR(256),
       uom VARCHAR(32),
       PRIMARY KEY (container_id, prop_key));
CREATE TABLE IF NOT EXISTS emc_tool_class (
       class_id VARCHAR(64) PRIMARY KEY,
       name VARCHAR(256) NOT NULL,
       description VARCHAR(512));
CREATE TABLE IF NOT EXISTS emc_tool (
       tool_id VARCHAR(64) PRIMARY KEY,
       class_id VARCHAR(64) NOT NULL,
       name VARCHAR(256) NOT NULL,
       description VARCHAR(512),
       hierarchy_scope_id VARCHAR(64),
       equipment_id VARCHAR(64),
       calibration_due DATE,
       status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE');
CREATE TABLE IF NOT EXISTS emc_tool_property (
       tool_id VARCHAR(64) NOT NULL,
       prop_key VARCHAR(64) NOT NULL,
       prop_value VARCHAR(256),
       uom VARCHAR(32),
       PRIMARY KEY (tool_id, prop_key));
CREATE TABLE IF NOT EXISTS emc_software_class (
       class_id VARCHAR(64) PRIMARY KEY,
       name VARCHAR(256) NOT NULL,
       description VARCHAR(512));
CREATE TABLE IF NOT EXISTS emc_software (
       software_id VARCHAR(64) PRIMARY KEY,
       class_id VARCHAR(64) NOT NULL,
       name VARCHAR(256) NOT NULL,
       description VARCHAR(512),
       hierarchy_scope_id VARCHAR(64),
       vendor VARCHAR(128),
       version_label VARCHAR(64),
       status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE');
CREATE TABLE IF NOT EXISTS emc_software_property (
       software_id VARCHAR(64) NOT NULL,
       prop_key VARCHAR(64) NOT NULL,
       prop_value VARCHAR(256),
       uom VARCHAR(32),
       PRIMARY KEY (software_id, prop_key));
CREATE TABLE IF NOT EXISTS emc_operations_definition (
       definition_id VARCHAR(64) NOT NULL,
       version VARCHAR(16) NOT NULL DEFAULT '1',
       name VARCHAR(256) NOT NULL,
       description VARCHAR(512),
       hierarchy_scope_id VARCHAR(64),
       published_flag VARCHAR(8) NOT NULL DEFAULT 'false',
       status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
       PRIMARY KEY (definition_id, version));
CREATE TABLE IF NOT EXISTS emc_operations_definition_segment (
       definition_id VARCHAR(64) NOT NULL,
       version VARCHAR(16) NOT NULL DEFAULT '1',
       segment_id VARCHAR(64) NOT NULL,
       sequence_no INTEGER NOT NULL DEFAULT 1,
       PRIMARY KEY (definition_id, version, segment_id));
CREATE TABLE IF NOT EXISTS emc_operations_schedule (
       schedule_id VARCHAR(64) PRIMARY KEY,
       name VARCHAR(256) NOT NULL,
       hierarchy_scope_id VARCHAR(64),
       start_time TIMESTAMP,
       end_time TIMESTAMP,
       state VARCHAR(32) NOT NULL DEFAULT 'RELEASED',
       description VARCHAR(512));
CREATE TABLE IF NOT EXISTS emc_operations_request (
       request_id VARCHAR(64) PRIMARY KEY,
       schedule_id VARCHAR(64) NOT NULL,
       definition_id VARCHAR(64),
       definition_version VARCHAR(16),
       priority INTEGER NOT NULL DEFAULT 5,
       requested_start TIMESTAMP,
       requested_end TIMESTAMP,
       state VARCHAR(32) NOT NULL DEFAULT 'RELEASED',
       description VARCHAR(512));
CREATE TABLE IF NOT EXISTS emc_resource_relationship_network (
       network_id VARCHAR(64) PRIMARY KEY,
       name VARCHAR(256) NOT NULL,
       description VARCHAR(512),
       hierarchy_scope_id VARCHAR(64),
       status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE');
CREATE TABLE IF NOT EXISTS emc_resource_relationship (
       rel_id VARCHAR(64) PRIMARY KEY,
       network_id VARCHAR(64) NOT NULL,
       from_resource_type VARCHAR(32) NOT NULL,
       from_resource_id VARCHAR(64) NOT NULL,
       to_resource_type VARCHAR(32) NOT NULL,
       to_resource_id VARCHAR(64) NOT NULL,
       relationship_type VARCHAR(64) NOT NULL,
       dependency VARCHAR(32) NOT NULL DEFAULT 'USES');
CREATE TABLE IF NOT EXISTS emc_resource_relationship_property (
       rel_id VARCHAR(64) NOT NULL,
       prop_key VARCHAR(64) NOT NULL,
       prop_value VARCHAR(256),
       uom VARCHAR(32),
       PRIMARY KEY (rel_id, prop_key));
CREATE TABLE IF NOT EXISTS emc_work_capability (
       capability_id VARCHAR(64) PRIMARY KEY,
       name VARCHAR(256) NOT NULL,
       description VARCHAR(512),
       hierarchy_scope_id VARCHAR(64),
       status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE');
CREATE TABLE IF NOT EXISTS emc_work_capability_equipment (
       capability_id VARCHAR(64) NOT NULL,
       equipment_id VARCHAR(64) NOT NULL DEFAULT '',
       equipment_class_id VARCHAR(64) NOT NULL DEFAULT '',
       quantity NUMERIC(14,3) NOT NULL DEFAULT 1,
       PRIMARY KEY (capability_id, equipment_id, equipment_class_id));
CREATE TABLE IF NOT EXISTS emc_work_capability_material (
       capability_id VARCHAR(64) NOT NULL,
       definition_id VARCHAR(64) NOT NULL DEFAULT '',
       material_class_id VARCHAR(64) NOT NULL DEFAULT '',
       quantity NUMERIC(14,3) NOT NULL DEFAULT 0,
       uom VARCHAR(16),
       PRIMARY KEY (capability_id, definition_id, material_class_id));
CREATE TABLE IF NOT EXISTS emc_work_capability_personnel (
       capability_id VARCHAR(64) NOT NULL,
       person_id VARCHAR(64) NOT NULL DEFAULT '',
       personnel_class_id VARCHAR(64) NOT NULL DEFAULT '',
       quantity NUMERIC(14,3) NOT NULL DEFAULT 1,
       PRIMARY KEY (capability_id, person_id, personnel_class_id));
CREATE TABLE IF NOT EXISTS emc_work_capability_segment (
       capability_id VARCHAR(64) NOT NULL,
       segment_id VARCHAR(64) NOT NULL,
       PRIMARY KEY (capability_id, segment_id));
CREATE TABLE IF NOT EXISTS emc_work_master_capability (
       work_master_id VARCHAR(64) NOT NULL,
       version VARCHAR(16) NOT NULL DEFAULT '1',
       capability_id VARCHAR(64) NOT NULL,
       effective_from TIMESTAMP,
       effective_to TIMESTAMP,
       PRIMARY KEY (work_master_id, version, capability_id));
CREATE TABLE IF NOT EXISTS emc_work_alert (
       alert_id VARCHAR(64) PRIMARY KEY,
       alert_type VARCHAR(64) NOT NULL,
       severity VARCHAR(32) NOT NULL DEFAULT 'WARNING',
       work_master_id VARCHAR(64),
       job_order_id VARCHAR(64),
       message VARCHAR(512) NOT NULL,
       status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
       raised_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
       ack_by VARCHAR(64),
       ack_at TIMESTAMP);
INSERT INTO emc_container_class (class_id, name, description, capacity_uom) SELECT 'CC-BIN', 'Storage bin', 'Bulk / WIP bin', 'kg' WHERE NOT EXISTS (SELECT 1 FROM emc_container_class WHERE class_id = 'CC-BIN');
INSERT INTO emc_container (container_id, class_id, name, description, hierarchy_scope_id, capacity, capacity_uom, status) SELECT 'CTR-BIN-A', 'CC-BIN', 'Line A bin', 'WIP bin at WU-A01', 'SCOPE-SITE-01', '500', 'kg', 'AVAILABLE' WHERE NOT EXISTS (SELECT 1 FROM emc_container WHERE container_id = 'CTR-BIN-A');
INSERT INTO emc_container_property (container_id, prop_key, prop_value, uom) SELECT 'CTR-BIN-A', 'tare_weight', '12.5', 'kg' WHERE NOT EXISTS (SELECT 1 FROM emc_container_property WHERE container_id = 'CTR-BIN-A' AND prop_key = 'tare_weight');
INSERT INTO emc_tool_class (class_id, name, description) SELECT 'TC-FIXTURE', 'Assembly fixture', 'Mechanical fixture / jig' WHERE NOT EXISTS (SELECT 1 FROM emc_tool_class WHERE class_id = 'TC-FIXTURE');
INSERT INTO emc_tool (tool_id, class_id, name, description, hierarchy_scope_id, equipment_id, status) SELECT 'TOOL-FIX-A01', 'TC-FIXTURE', 'Fixture A01', 'Assembly fixture for WU-A01', 'SCOPE-SITE-01', 'WU-A01', 'AVAILABLE' WHERE NOT EXISTS (SELECT 1 FROM emc_tool WHERE tool_id = 'TOOL-FIX-A01');
INSERT INTO emc_tool_property (tool_id, prop_key, prop_value, uom) SELECT 'TOOL-FIX-A01', 'max_cycles', '10000', '1' WHERE NOT EXISTS (SELECT 1 FROM emc_tool_property WHERE tool_id = 'TOOL-FIX-A01' AND prop_key = 'max_cycles');
INSERT INTO emc_software_class (class_id, name, description) SELECT 'SC-MES-AGENT', 'MES agent runtime', 'Edge / hub agent software' WHERE NOT EXISTS (SELECT 1 FROM emc_software_class WHERE class_id = 'SC-MES-AGENT');
INSERT INTO emc_software (software_id, class_id, name, description, hierarchy_scope_id, vendor, version_label, status) SELECT 'SW-MES-CORE', 'SC-MES-AGENT', 'ERP-MES Core agent', 'Demostand MES hub software', 'SCOPE-SITE-01', 'IoT Solutions', '2.2.0', 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM emc_software WHERE software_id = 'SW-MES-CORE');
INSERT INTO emc_software_property (software_id, prop_key, prop_value, uom) SELECT 'SW-MES-CORE', 'license', 'demo', NULL WHERE NOT EXISTS (SELECT 1 FROM emc_software_property WHERE software_id = 'SW-MES-CORE' AND prop_key = 'license');
INSERT INTO emc_operations_definition (definition_id, version, name, description, hierarchy_scope_id, published_flag, status) SELECT 'OD-ASSEMBLY-01', '1', 'Assembly operations definition', 'GOST Part 2 Operations Definition for assemble+pack', 'SCOPE-SITE-01', 'true', 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM emc_operations_definition WHERE definition_id = 'OD-ASSEMBLY-01' AND version = '1');
INSERT INTO emc_operations_definition_segment (definition_id, version, segment_id, sequence_no) SELECT 'OD-ASSEMBLY-01', '1', 'SEG-ASSEMBLE', '1' WHERE NOT EXISTS (SELECT 1 FROM emc_operations_definition_segment WHERE definition_id = 'OD-ASSEMBLY-01' AND version = '1' AND segment_id = 'SEG-ASSEMBLE');
INSERT INTO emc_operations_definition_segment (definition_id, version, segment_id, sequence_no) SELECT 'OD-ASSEMBLY-01', '1', 'SEG-PACK', '2' WHERE NOT EXISTS (SELECT 1 FROM emc_operations_definition_segment WHERE definition_id = 'OD-ASSEMBLY-01' AND version = '1' AND segment_id = 'SEG-PACK');
INSERT INTO emc_operations_schedule (schedule_id, name, hierarchy_scope_id, state, description) SELECT 'OS-DEMO-001', 'Demo operations schedule', 'SCOPE-SITE-01', 'RELEASED', 'Firm schedule linked to OD-ASSEMBLY-01' WHERE NOT EXISTS (SELECT 1 FROM emc_operations_schedule WHERE schedule_id = 'OS-DEMO-001');
INSERT INTO emc_operations_request (request_id, schedule_id, definition_id, definition_version, priority, state, description) SELECT 'OR-DEMO-001', 'OS-DEMO-001', 'OD-ASSEMBLY-01', '1', '3', 'RELEASED', 'Request for assembly definition' WHERE NOT EXISTS (SELECT 1 FROM emc_operations_request WHERE request_id = 'OR-DEMO-001');
INSERT INTO emc_resource_relationship_network (network_id, name, description, hierarchy_scope_id, status) SELECT 'RRN-SITE-01', 'Site-01 resource network', 'Equipment uses tools/containers/software', 'SCOPE-SITE-01', 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM emc_resource_relationship_network WHERE network_id = 'RRN-SITE-01');
INSERT INTO emc_resource_relationship (rel_id, network_id, from_resource_type, from_resource_id, to_resource_type, to_resource_id, relationship_type, dependency) SELECT 'RR-WU-A01-TOOL', 'RRN-SITE-01', 'EQUIPMENT', 'WU-A01', 'TOOL', 'TOOL-FIX-A01', 'USES', 'REQUIRED' WHERE NOT EXISTS (SELECT 1 FROM emc_resource_relationship WHERE rel_id = 'RR-WU-A01-TOOL');
INSERT INTO emc_resource_relationship (rel_id, network_id, from_resource_type, from_resource_id, to_resource_type, to_resource_id, relationship_type, dependency) SELECT 'RR-WU-A01-CTR', 'RRN-SITE-01', 'EQUIPMENT', 'WU-A01', 'CONTAINER', 'CTR-BIN-A', 'USES', 'OPTIONAL' WHERE NOT EXISTS (SELECT 1 FROM emc_resource_relationship WHERE rel_id = 'RR-WU-A01-CTR');
INSERT INTO emc_resource_relationship (rel_id, network_id, from_resource_type, from_resource_id, to_resource_type, to_resource_id, relationship_type, dependency) SELECT 'RR-SITE-SW', 'RRN-SITE-01', 'EQUIPMENT', 'SITE-01', 'SOFTWARE', 'SW-MES-CORE', 'RUNS', 'REQUIRED' WHERE NOT EXISTS (SELECT 1 FROM emc_resource_relationship WHERE rel_id = 'RR-SITE-SW');
INSERT INTO emc_resource_relationship_property (rel_id, prop_key, prop_value, uom) SELECT 'RR-WU-A01-TOOL', 'setup_min', '5', 'min' WHERE NOT EXISTS (SELECT 1 FROM emc_resource_relationship_property WHERE rel_id = 'RR-WU-A01-TOOL' AND prop_key = 'setup_min');
INSERT INTO emc_work_capability (capability_id, name, description, hierarchy_scope_id, status) SELECT 'WC-ASSEMBLE-A01', 'Assemble capability WU-A01', 'Work capability for assembly at WU-A01', 'SCOPE-SITE-01', 'AVAILABLE' WHERE NOT EXISTS (SELECT 1 FROM emc_work_capability WHERE capability_id = 'WC-ASSEMBLE-A01');
INSERT INTO emc_work_capability_equipment (capability_id, equipment_id, equipment_class_id, quantity) SELECT 'WC-ASSEMBLE-A01', 'WU-A01', 'EQC-ASSEMBLY-MACHINE', '1' WHERE NOT EXISTS (SELECT 1 FROM emc_work_capability_equipment WHERE capability_id = 'WC-ASSEMBLE-A01' AND equipment_id = 'WU-A01');
INSERT INTO emc_work_capability_material (capability_id, definition_id, material_class_id, quantity, uom) SELECT 'WC-ASSEMBLE-A01', 'RAW-PLASTIC-GRANULE', 'MCL-RAW', '2.5', 'kg' WHERE NOT EXISTS (SELECT 1 FROM emc_work_capability_material WHERE capability_id = 'WC-ASSEMBLE-A01' AND definition_id = 'RAW-PLASTIC-GRANULE');
INSERT INTO emc_work_capability_personnel (capability_id, person_id, personnel_class_id, quantity) SELECT 'WC-ASSEMBLE-A01', 'EMP-001', 'PCL-OPERATOR', '1' WHERE NOT EXISTS (SELECT 1 FROM emc_work_capability_personnel WHERE capability_id = 'WC-ASSEMBLE-A01' AND person_id = 'EMP-001');
INSERT INTO emc_work_capability_segment (capability_id, segment_id) SELECT 'WC-ASSEMBLE-A01', 'SEG-ASSEMBLE' WHERE NOT EXISTS (SELECT 1 FROM emc_work_capability_segment WHERE capability_id = 'WC-ASSEMBLE-A01' AND segment_id = 'SEG-ASSEMBLE');
INSERT INTO emc_work_master_capability (work_master_id, version, capability_id) SELECT 'WM-ASSEMBLE', '1', 'WC-ASSEMBLE-A01' WHERE NOT EXISTS (SELECT 1 FROM emc_work_master_capability WHERE work_master_id = 'WM-ASSEMBLE' AND version = '1' AND capability_id = 'WC-ASSEMBLE-A01');
INSERT INTO emc_work_alert (alert_id, alert_type, severity, work_master_id, job_order_id, message, status) SELECT 'WA-DEMO-001', 'RESOURCE_SHORTAGE', 'WARNING', 'WM-ASSEMBLE', NULL, 'Fixture TOOL-FIX-A01 calibration due soon', 'OPEN' WHERE NOT EXISTS (SELECT 1 FROM emc_work_alert WHERE alert_id = 'WA-DEMO-001');
INSERT INTO emc_kpi_value (id, kpi_code, scope_id, period_label, value_num) SELECT 'KPI-DEMO-OEE', 'OEE', 'SCOPE-SITE-01', 'DEMO-SHIFT', '82.5' WHERE NOT EXISTS (SELECT 1 FROM emc_kpi_value WHERE id = 'KPI-DEMO-OEE');
INSERT INTO emc_maintenance_request (request_id, equipment_id, description, priority, status) SELECT 'MR-DEMO-001', 'WU-A01', 'Bearing noise on assembly cell', '2', 'NEW' WHERE NOT EXISTS (SELECT 1 FROM emc_maintenance_request WHERE request_id = 'MR-DEMO-001');
INSERT INTO emc_maintenance_work_order (wo_id, request_id, equipment_id, status) SELECT 'MWO-DEMO-001', 'MR-DEMO-001', 'WU-A01', 'PLANNED' WHERE NOT EXISTS (SELECT 1 FROM emc_maintenance_work_order WHERE wo_id = 'MWO-DEMO-001');
UPDATE emc_maintenance_request SET status = 'ACCEPTED'
       WHERE request_id = 'MR-DEMO-001' AND status = 'NEW';
INSERT INTO emc_qa_test_result (id, job_no, lot_id, test_name, result, measurements_json)
       SELECT gen_random_uuid(), NULL, 'LOT-FG-0001', 'Visual inspection', 'PASS', '{"score":"OK"}'
       WHERE NOT EXISTS (
         SELECT 1 FROM emc_qa_test_result
         WHERE test_name = 'Visual inspection' AND lot_id = 'LOT-FG-0001');
INSERT INTO emc_inventory_document (doc_id, kind, status, operator_person_id) SELECT 'INV-DEMO-001', 'TRANSFER', 'DRAFT', 'EMP-001' WHERE NOT EXISTS (SELECT 1 FROM emc_inventory_document WHERE doc_id = 'INV-DEMO-001');
INSERT INTO emc_inventory_document_line
       (line_id, doc_id, definition_id, lot_id, quantity, source_location, dest_location)
       SELECT gen_random_uuid(), 'INV-DEMO-001', 'RAW-PLASTIC-GRANULE', NULL, 10,
              'WH-CENTRAL', 'WH-LINE-A01'
       WHERE NOT EXISTS (
         SELECT 1 FROM emc_inventory_document_line WHERE doc_id = 'INV-DEMO-001')
