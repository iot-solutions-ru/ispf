CREATE TABLE IF NOT EXISTS emc_defect_type (
       defect_type_id VARCHAR(64) PRIMARY KEY,
       description VARCHAR(256),
       category VARCHAR(32) DEFAULT 'QC');
CREATE TABLE IF NOT EXISTS emc_reason_code (
       reason_code VARCHAR(64) PRIMARY KEY,
       parent_code VARCHAR(64),
       description VARCHAR(256),
       default_defect_type_id VARCHAR(64));
CREATE TABLE IF NOT EXISTS emc_defect_record (
       defect_id UUID PRIMARY KEY,
       defect_no VARCHAR(64) NOT NULL UNIQUE,
       job_no VARCHAR(64) NOT NULL,
       lot_id VARCHAR(64),
       defect_type_id VARCHAR(64) NOT NULL,
       reason_code VARCHAR(64),
       severity VARCHAR(32) NOT NULL DEFAULT 'MINOR',
       qty_declared NUMERIC(14,3) NOT NULL DEFAULT 0,
       qty_confirmed NUMERIC(14,3),
       status VARCHAR(32) NOT NULL DEFAULT 'REGISTERED',
       created_by VARCHAR(64),
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS emc_defect_status_history (
       id UUID PRIMARY KEY,
       defect_no VARCHAR(64) NOT NULL,
       from_status VARCHAR(32),
       to_status VARCHAR(32) NOT NULL,
       actor VARCHAR(64),
       note VARCHAR(512),
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS emc_qa_test_result (
       id UUID PRIMARY KEY,
       job_no VARCHAR(64),
       lot_id VARCHAR(64),
       test_name VARCHAR(128) NOT NULL,
       result VARCHAR(16) NOT NULL,
       measurements_json VARCHAR(2048),
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
INSERT INTO emc_defect_type (defect_type_id, description, category) SELECT 'DFT-VISUAL', 'Visual defect', 'QC' WHERE NOT EXISTS (SELECT 1 FROM emc_defect_type WHERE defect_type_id = 'DFT-VISUAL');
INSERT INTO emc_defect_type (defect_type_id, description, category) SELECT 'DFT-DIMENSION', 'Dimension out of tolerance', 'QC' WHERE NOT EXISTS (SELECT 1 FROM emc_defect_type WHERE defect_type_id = 'DFT-DIMENSION');
INSERT INTO emc_defect_type (defect_type_id, description, category) SELECT 'DFT-FUNCTIONAL', 'Functional failure', 'QC' WHERE NOT EXISTS (SELECT 1 FROM emc_defect_type WHERE defect_type_id = 'DFT-FUNCTIONAL');
INSERT INTO emc_reason_code (reason_code, parent_code, description, default_defect_type_id) SELECT 'RC-MATERIAL', NULL, 'Material-caused', 'DFT-VISUAL' WHERE NOT EXISTS (SELECT 1 FROM emc_reason_code WHERE reason_code = 'RC-MATERIAL');
INSERT INTO emc_reason_code (reason_code, parent_code, description, default_defect_type_id) SELECT 'RC-MACHINE', NULL, 'Machine-caused', 'DFT-DIMENSION' WHERE NOT EXISTS (SELECT 1 FROM emc_reason_code WHERE reason_code = 'RC-MACHINE');
INSERT INTO emc_reason_code (reason_code, parent_code, description, default_defect_type_id) SELECT 'RC-HUMAN', NULL, 'Human error', 'DFT-VISUAL' WHERE NOT EXISTS (SELECT 1 FROM emc_reason_code WHERE reason_code = 'RC-HUMAN');
INSERT INTO emc_reason_code (reason_code, parent_code, description, default_defect_type_id) SELECT 'RC-METHOD', NULL, 'Method/process-caused', 'DFT-FUNCTIONAL' WHERE NOT EXISTS (SELECT 1 FROM emc_reason_code WHERE reason_code = 'RC-METHOD')
