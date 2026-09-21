CREATE TABLE IF NOT EXISTS emc_equipment_class (
       class_id VARCHAR(64) PRIMARY KEY,
       description VARCHAR(256),
       equipment_level VARCHAR(32) NOT NULL,
       parent_class_id VARCHAR(64));
CREATE TABLE IF NOT EXISTS emc_equipment (
       equipment_id VARCHAR(64) PRIMARY KEY,
       class_id VARCHAR(64),
       equipment_level VARCHAR(32) NOT NULL,
       parent_id VARCHAR(64),
       hierarchy_path VARCHAR(512),
       description VARCHAR(256));
CREATE TABLE IF NOT EXISTS emc_equipment_property (
       equipment_id VARCHAR(64) NOT NULL,
       prop_key VARCHAR(64) NOT NULL,
       prop_value VARCHAR(512),
       uom VARCHAR(32));
CREATE TABLE IF NOT EXISTS emc_personnel_class (
       class_id VARCHAR(64) PRIMARY KEY,
       description VARCHAR(256));
CREATE TABLE IF NOT EXISTS emc_person (
       person_id VARCHAR(64) PRIMARY KEY,
       person_name VARCHAR(256) NOT NULL,
       personnel_class_id VARCHAR(64));
CREATE TABLE IF NOT EXISTS emc_person_qualification (
       person_id VARCHAR(64) NOT NULL,
       equipment_id VARCHAR(64),
       equipment_class_id VARCHAR(64),
       qualification VARCHAR(128) DEFAULT 'OPERATE');
INSERT INTO emc_equipment_class (class_id, description, equipment_level, parent_class_id) SELECT 'EQC-ASSEMBLY-MACHINE', 'Assembly machine class', 'WORK_UNIT', NULL WHERE NOT EXISTS (SELECT 1 FROM emc_equipment_class WHERE class_id = 'EQC-ASSEMBLY-MACHINE');
INSERT INTO emc_equipment_class (class_id, description, equipment_level, parent_class_id) SELECT 'EQC-PACK-MACHINE', 'Packaging machine class', 'WORK_UNIT', NULL WHERE NOT EXISTS (SELECT 1 FROM emc_equipment_class WHERE class_id = 'EQC-PACK-MACHINE');
INSERT INTO emc_equipment (equipment_id, class_id, equipment_level, parent_id, hierarchy_path, description) SELECT 'ENT-DEMO', NULL, 'ENTERPRISE', NULL, 'ENT-DEMO', 'Demo enterprise' WHERE NOT EXISTS (SELECT 1 FROM emc_equipment WHERE equipment_id = 'ENT-DEMO');
INSERT INTO emc_equipment (equipment_id, class_id, equipment_level, parent_id, hierarchy_path, description) SELECT 'SITE-01', NULL, 'SITE', 'ENT-DEMO', 'ENT-DEMO/SITE-01', 'Demo site' WHERE NOT EXISTS (SELECT 1 FROM emc_equipment WHERE equipment_id = 'SITE-01');
INSERT INTO emc_equipment (equipment_id, class_id, equipment_level, parent_id, hierarchy_path, description) SELECT 'AREA-PROD', NULL, 'AREA', 'SITE-01', 'ENT-DEMO/SITE-01/AREA-PROD', 'Production area' WHERE NOT EXISTS (SELECT 1 FROM emc_equipment WHERE equipment_id = 'AREA-PROD');
INSERT INTO emc_equipment (equipment_id, class_id, equipment_level, parent_id, hierarchy_path, description) SELECT 'LINE-A', NULL, 'WORK_CENTER', 'AREA-PROD', 'ENT-DEMO/SITE-01/AREA-PROD/LINE-A', 'Production line A (work center)' WHERE NOT EXISTS (SELECT 1 FROM emc_equipment WHERE equipment_id = 'LINE-A');
INSERT INTO emc_equipment (equipment_id, class_id, equipment_level, parent_id, hierarchy_path, description) SELECT 'WU-A01', 'EQC-ASSEMBLY-MACHINE', 'WORK_UNIT', 'LINE-A', 'ENT-DEMO/SITE-01/AREA-PROD/LINE-A/WU-A01', 'Assembly work unit A01' WHERE NOT EXISTS (SELECT 1 FROM emc_equipment WHERE equipment_id = 'WU-A01');
INSERT INTO emc_equipment (equipment_id, class_id, equipment_level, parent_id, hierarchy_path, description) SELECT 'WU-A02', 'EQC-PACK-MACHINE', 'WORK_UNIT', 'LINE-A', 'ENT-DEMO/SITE-01/AREA-PROD/LINE-A/WU-A02', 'Packaging work unit A02' WHERE NOT EXISTS (SELECT 1 FROM emc_equipment WHERE equipment_id = 'WU-A02');
INSERT INTO emc_equipment (equipment_id, class_id, equipment_level, parent_id, hierarchy_path, description) SELECT 'WH-CENTRAL', NULL, 'STORAGE_ZONE', 'SITE-01', 'ENT-DEMO/SITE-01/WH-CENTRAL', 'Central warehouse (storage zone)' WHERE NOT EXISTS (SELECT 1 FROM emc_equipment WHERE equipment_id = 'WH-CENTRAL');
INSERT INTO emc_equipment (equipment_id, class_id, equipment_level, parent_id, hierarchy_path, description) SELECT 'WH-LINE-A01', NULL, 'STORAGE_UNIT', 'WH-CENTRAL', 'ENT-DEMO/SITE-01/WH-CENTRAL/WH-LINE-A01', 'Line-side storage A01' WHERE NOT EXISTS (SELECT 1 FROM emc_equipment WHERE equipment_id = 'WH-LINE-A01');
INSERT INTO emc_personnel_class (class_id, description) SELECT 'PCL-OPERATOR', 'Line operator' WHERE NOT EXISTS (SELECT 1 FROM emc_personnel_class WHERE class_id = 'PCL-OPERATOR');
INSERT INTO emc_personnel_class (class_id, description) SELECT 'PCL-SUPERVISOR', 'Shift supervisor' WHERE NOT EXISTS (SELECT 1 FROM emc_personnel_class WHERE class_id = 'PCL-SUPERVISOR');
INSERT INTO emc_person (person_id, person_name, personnel_class_id) SELECT 'EMP-001', 'Ivan Operator', 'PCL-OPERATOR' WHERE NOT EXISTS (SELECT 1 FROM emc_person WHERE person_id = 'EMP-001');
INSERT INTO emc_person (person_id, person_name, personnel_class_id) SELECT 'EMP-002', 'Petr Supervisor', 'PCL-SUPERVISOR' WHERE NOT EXISTS (SELECT 1 FROM emc_person WHERE person_id = 'EMP-002');
INSERT INTO emc_person (person_id, person_name, personnel_class_id) SELECT 'EMP-003', 'Anna Operator', 'PCL-OPERATOR' WHERE NOT EXISTS (SELECT 1 FROM emc_person WHERE person_id = 'EMP-003');
INSERT INTO emc_person_qualification (person_id, equipment_id, equipment_class_id, qualification) SELECT 'EMP-001', 'WU-A01', NULL, 'OPERATE' WHERE NOT EXISTS (SELECT 1 FROM emc_person_qualification WHERE person_id = 'EMP-001' AND equipment_id = 'WU-A01');
INSERT INTO emc_person_qualification (person_id, equipment_id, equipment_class_id, qualification) SELECT 'EMP-003', NULL, 'EQC-PACK-MACHINE', 'OPERATE' WHERE NOT EXISTS (SELECT 1 FROM emc_person_qualification WHERE person_id = 'EMP-003' AND equipment_class_id = 'EQC-PACK-MACHINE')
