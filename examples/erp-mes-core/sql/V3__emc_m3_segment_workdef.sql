CREATE TABLE IF NOT EXISTS emc_process_segment (
       segment_id VARCHAR(64) PRIMARY KEY,
       parent_id VARCHAR(64),
       operations_type VARCHAR(32) NOT NULL DEFAULT 'PRODUCTION',
       name VARCHAR(256) NOT NULL,
       description VARCHAR(512));
CREATE TABLE IF NOT EXISTS emc_segment_material_spec (
       spec_id VARCHAR(96) PRIMARY KEY,
       segment_id VARCHAR(64) NOT NULL,
       material_class_id VARCHAR(64),
       definition_id VARCHAR(64),
       material_use VARCHAR(32) NOT NULL,
       quantity NUMERIC(14,3) NOT NULL DEFAULT 0,
       uom VARCHAR(16));
CREATE TABLE IF NOT EXISTS emc_segment_equipment_spec (
       spec_id VARCHAR(96) PRIMARY KEY,
       segment_id VARCHAR(64) NOT NULL,
       equipment_class_id VARCHAR(64),
       equipment_id VARCHAR(64),
       equipment_use VARCHAR(32) NOT NULL DEFAULT 'PRIMARY',
       quantity NUMERIC(14,3) NOT NULL DEFAULT 1);
CREATE TABLE IF NOT EXISTS emc_segment_personnel_spec (
       spec_id VARCHAR(96) PRIMARY KEY,
       segment_id VARCHAR(64) NOT NULL,
       personnel_class_id VARCHAR(64),
       person_id VARCHAR(64),
       personnel_use VARCHAR(32) NOT NULL DEFAULT 'OPERATOR',
       quantity NUMERIC(14,3) NOT NULL DEFAULT 1);
CREATE TABLE IF NOT EXISTS emc_work_master (
       work_master_id VARCHAR(64) NOT NULL,
       version VARCHAR(16) NOT NULL DEFAULT '1',
       segment_id VARCHAR(64) NOT NULL,
       duration_min NUMERIC(10,1),
       description VARCHAR(256),
       PRIMARY KEY (work_master_id, version));
INSERT INTO emc_process_segment (segment_id, parent_id, operations_type, name, description) SELECT 'SEG-ASSEMBLE', NULL, 'PRODUCTION', 'Assembly', 'Assemble housing from granulate' WHERE NOT EXISTS (SELECT 1 FROM emc_process_segment WHERE segment_id = 'SEG-ASSEMBLE');
INSERT INTO emc_process_segment (segment_id, parent_id, operations_type, name, description) SELECT 'SEG-PACK', NULL, 'PRODUCTION', 'Packing', 'Pack housing into boxes' WHERE NOT EXISTS (SELECT 1 FROM emc_process_segment WHERE segment_id = 'SEG-PACK');
INSERT INTO emc_segment_material_spec (spec_id, segment_id, material_class_id, definition_id, material_use, quantity, uom) SELECT 'SEG-ASSEMBLE:IN-GRANULE', 'SEG-ASSEMBLE', NULL, 'RAW-PLASTIC-GRANULE', 'CONSUMED', '2.5', 'kg' WHERE NOT EXISTS (SELECT 1 FROM emc_segment_material_spec WHERE spec_id = 'SEG-ASSEMBLE:IN-GRANULE');
INSERT INTO emc_segment_material_spec (spec_id, segment_id, material_class_id, definition_id, material_use, quantity, uom) SELECT 'SEG-ASSEMBLE:OUT-HOUSING', 'SEG-ASSEMBLE', NULL, 'WIP-HOUSING', 'PRODUCED', '1', 'pcs' WHERE NOT EXISTS (SELECT 1 FROM emc_segment_material_spec WHERE spec_id = 'SEG-ASSEMBLE:OUT-HOUSING');
INSERT INTO emc_segment_material_spec (spec_id, segment_id, material_class_id, definition_id, material_use, quantity, uom) SELECT 'SEG-PACK:IN-HOUSING', 'SEG-PACK', NULL, 'WIP-HOUSING', 'CONSUMED', '1', 'pcs' WHERE NOT EXISTS (SELECT 1 FROM emc_segment_material_spec WHERE spec_id = 'SEG-PACK:IN-HOUSING');
INSERT INTO emc_segment_material_spec (spec_id, segment_id, material_class_id, definition_id, material_use, quantity, uom) SELECT 'SEG-PACK:IN-BOX', 'SEG-PACK', NULL, 'RAW-PACKAGING-BOX', 'CONSUMED', '1', 'pcs' WHERE NOT EXISTS (SELECT 1 FROM emc_segment_material_spec WHERE spec_id = 'SEG-PACK:IN-BOX');
INSERT INTO emc_segment_material_spec (spec_id, segment_id, material_class_id, definition_id, material_use, quantity, uom) SELECT 'SEG-PACK:OUT-FG', 'SEG-PACK', NULL, 'FG-UNIT-PACKED', 'PRODUCED', '1', 'pcs' WHERE NOT EXISTS (SELECT 1 FROM emc_segment_material_spec WHERE spec_id = 'SEG-PACK:OUT-FG');
INSERT INTO emc_segment_equipment_spec (spec_id, segment_id, equipment_class_id, equipment_id, equipment_use, quantity) SELECT 'SEG-ASSEMBLE:EQ', 'SEG-ASSEMBLE', 'EQC-ASSEMBLY-MACHINE', NULL, 'PRIMARY', '1' WHERE NOT EXISTS (SELECT 1 FROM emc_segment_equipment_spec WHERE spec_id = 'SEG-ASSEMBLE:EQ');
INSERT INTO emc_segment_equipment_spec (spec_id, segment_id, equipment_class_id, equipment_id, equipment_use, quantity) SELECT 'SEG-PACK:EQ', 'SEG-PACK', 'EQC-PACK-MACHINE', NULL, 'PRIMARY', '1' WHERE NOT EXISTS (SELECT 1 FROM emc_segment_equipment_spec WHERE spec_id = 'SEG-PACK:EQ');
INSERT INTO emc_segment_personnel_spec (spec_id, segment_id, personnel_class_id, person_id, personnel_use, quantity) SELECT 'SEG-ASSEMBLE:PERS', 'SEG-ASSEMBLE', 'PCL-OPERATOR', NULL, 'OPERATOR', '1' WHERE NOT EXISTS (SELECT 1 FROM emc_segment_personnel_spec WHERE spec_id = 'SEG-ASSEMBLE:PERS');
INSERT INTO emc_segment_personnel_spec (spec_id, segment_id, personnel_class_id, person_id, personnel_use, quantity) SELECT 'SEG-PACK:PERS', 'SEG-PACK', 'PCL-OPERATOR', NULL, 'OPERATOR', '1' WHERE NOT EXISTS (SELECT 1 FROM emc_segment_personnel_spec WHERE spec_id = 'SEG-PACK:PERS');
INSERT INTO emc_work_master (work_master_id, version, segment_id, duration_min, description) SELECT 'WM-ASSEMBLE', '1', 'SEG-ASSEMBLE', '60', 'Assemble housing (master)' WHERE NOT EXISTS (SELECT 1 FROM emc_work_master WHERE work_master_id = 'WM-ASSEMBLE' AND version = '1');
INSERT INTO emc_work_master (work_master_id, version, segment_id, duration_min, description) SELECT 'WM-PACK', '1', 'SEG-PACK', '30', 'Pack housing (master)' WHERE NOT EXISTS (SELECT 1 FROM emc_work_master WHERE work_master_id = 'WM-PACK' AND version = '1')
