CREATE TABLE IF NOT EXISTS emc_erp_transaction_profile (
       verb VARCHAR(32) NOT NULL,
       noun VARCHAR(64) NOT NULL,
       direction VARCHAR(8) NOT NULL,
       description VARCHAR(256),
       PRIMARY KEY (verb, noun, direction));
CREATE TABLE IF NOT EXISTS emc_kpi_definition (
       kpi_code VARCHAR(64) PRIMARY KEY,
       name VARCHAR(256) NOT NULL,
       iso22400_id VARCHAR(64),
       unit VARCHAR(32),
       description VARCHAR(512));
CREATE TABLE IF NOT EXISTS emc_kpi_value (
       id VARCHAR(64) PRIMARY KEY,
       kpi_code VARCHAR(64) NOT NULL,
       scope_id VARCHAR(64),
       period_label VARCHAR(128),
       value_num DOUBLE PRECISION,
       calculated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);
INSERT INTO emc_erp_transaction_profile (verb, noun, direction, description) SELECT 'PROCESS', 'OPERATIONS_EVENT', 'OUT', 'Work commenced / downtime events' WHERE NOT EXISTS (SELECT 1 FROM emc_erp_transaction_profile WHERE verb='PROCESS' AND noun='OPERATIONS_EVENT' AND direction='OUT');
INSERT INTO emc_erp_transaction_profile (verb, noun, direction, description) SELECT 'PROCESS', 'OPERATIONS_PERFORMANCE', 'OUT', 'Job completed performance' WHERE NOT EXISTS (SELECT 1 FROM emc_erp_transaction_profile WHERE verb='PROCESS' AND noun='OPERATIONS_PERFORMANCE' AND direction='OUT');
INSERT INTO emc_erp_transaction_profile (verb, noun, direction, description) SELECT 'PROCESS', 'MATERIAL_LOT', 'OUT', 'Inventory / lot movement' WHERE NOT EXISTS (SELECT 1 FROM emc_erp_transaction_profile WHERE verb='PROCESS' AND noun='MATERIAL_LOT' AND direction='OUT');
INSERT INTO emc_erp_transaction_profile (verb, noun, direction, description) SELECT 'PROCESS', 'OPERATIONS_SCHEDULE', 'IN', 'Inbound firm schedule' WHERE NOT EXISTS (SELECT 1 FROM emc_erp_transaction_profile WHERE verb='PROCESS' AND noun='OPERATIONS_SCHEDULE' AND direction='IN');
INSERT INTO emc_erp_transaction_profile (verb, noun, direction, description) SELECT 'SYNC', 'MASTER_DATA', 'IN', 'Inbound master data replica' WHERE NOT EXISTS (SELECT 1 FROM emc_erp_transaction_profile WHERE verb='SYNC' AND noun='MASTER_DATA' AND direction='IN');
INSERT INTO emc_erp_transaction_profile (verb, noun, direction, description) SELECT 'GET', 'OPERATIONS_CAPABILITY', 'IN', 'Capability query from L4' WHERE NOT EXISTS (SELECT 1 FROM emc_erp_transaction_profile WHERE verb='GET' AND noun='OPERATIONS_CAPABILITY' AND direction='IN');
INSERT INTO emc_erp_transaction_profile (verb, noun, direction, description) SELECT 'SHOW', 'OPERATIONS_DEFINITION', 'IN', 'Show operations definition' WHERE NOT EXISTS (SELECT 1 FROM emc_erp_transaction_profile WHERE verb='SHOW' AND noun='OPERATIONS_DEFINITION' AND direction='IN');
INSERT INTO emc_erp_transaction_profile (verb, noun, direction, description) SELECT 'SHOW', 'PRODUCT_DEFINITION', 'IN', 'Show product definition' WHERE NOT EXISTS (SELECT 1 FROM emc_erp_transaction_profile WHERE verb='SHOW' AND noun='PRODUCT_DEFINITION' AND direction='IN');
INSERT INTO emc_kpi_definition (kpi_code, name, iso22400_id, unit, description) SELECT 'OEE', 'Overall Equipment Effectiveness', 'PE001', '%', 'A×P×Q' WHERE NOT EXISTS (SELECT 1 FROM emc_kpi_definition WHERE kpi_code='OEE');
INSERT INTO emc_kpi_definition (kpi_code, name, iso22400_id, unit, description) SELECT 'AVAILABILITY', 'Availability', 'PE002', '%', 'Planned vs availability loss' WHERE NOT EXISTS (SELECT 1 FROM emc_kpi_definition WHERE kpi_code='AVAILABILITY');
INSERT INTO emc_kpi_definition (kpi_code, name, iso22400_id, unit, description) SELECT 'PERFORMANCE', 'Performance Efficiency', 'PE003', '%', 'Speed / performance losses' WHERE NOT EXISTS (SELECT 1 FROM emc_kpi_definition WHERE kpi_code='PERFORMANCE');
INSERT INTO emc_kpi_definition (kpi_code, name, iso22400_id, unit, description) SELECT 'QUALITY', 'Quality Ratio', 'PE004', '%', 'Good vs defective' WHERE NOT EXISTS (SELECT 1 FROM emc_kpi_definition WHERE kpi_code='QUALITY');
INSERT INTO emc_kpi_definition (kpi_code, name, iso22400_id, unit, description) SELECT 'SETUP_RATIO', 'Setup Ratio', 'PE016', '%', 'Setup time / planned' WHERE NOT EXISTS (SELECT 1 FROM emc_kpi_definition WHERE kpi_code='SETUP_RATIO');
INSERT INTO emc_kpi_definition (kpi_code, name, iso22400_id, unit, description) SELECT 'SCRAP_RATIO', 'Scrap Ratio', 'PE010', '%', 'Scrap / produced' WHERE NOT EXISTS (SELECT 1 FROM emc_kpi_definition WHERE kpi_code='SCRAP_RATIO');
INSERT INTO emc_kpi_definition (kpi_code, name, iso22400_id, unit, description) SELECT 'DEFECT_RATE', 'Defect Rate', 'PE011', '%', 'Defect qty / jobs' WHERE NOT EXISTS (SELECT 1 FROM emc_kpi_definition WHERE kpi_code='DEFECT_RATE');
INSERT INTO emc_kpi_definition (kpi_code, name, iso22400_id, unit, description) SELECT 'INV_TURNS', 'Inventory Turns', 'PE022', '1', 'Movement / average stock' WHERE NOT EXISTS (SELECT 1 FROM emc_kpi_definition WHERE kpi_code='INV_TURNS');
INSERT INTO emc_kpi_definition (kpi_code, name, iso22400_id, unit, description) SELECT 'MTTR', 'Mean Time To Repair', 'PE006', 'min', 'From closed downtime events' WHERE NOT EXISTS (SELECT 1 FROM emc_kpi_definition WHERE kpi_code='MTTR');
INSERT INTO emc_kpi_definition (kpi_code, name, iso22400_id, unit, description) SELECT 'MTBF', 'Mean Time Between Failures', 'PE007', 'min', 'From downtime events' WHERE NOT EXISTS (SELECT 1 FROM emc_kpi_definition WHERE kpi_code='MTBF');
INSERT INTO emc_operations_capability (capability_id, operations_type, equipment_id, segment_id, reason, status) SELECT 'CAP-WU-A02-PACK', 'PRODUCTION', 'WU-A02', 'SEG-PACK', 'Pack cell capability', 'AVAILABLE' WHERE NOT EXISTS (SELECT 1 FROM emc_operations_capability WHERE capability_id = 'CAP-WU-A02-PACK');
INSERT INTO emc_ops_capability_equipment (capability_id, equipment_id, equipment_class_id, quantity) SELECT 'CAP-WU-A02-PACK', 'WU-A02', 'EQC-PACK-MACHINE', '1' WHERE NOT EXISTS (SELECT 1 FROM emc_ops_capability_equipment WHERE capability_id = 'CAP-WU-A02-PACK' AND equipment_id = 'WU-A02')
