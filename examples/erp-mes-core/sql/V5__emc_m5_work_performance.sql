CREATE TABLE IF NOT EXISTS emc_job_response (
       response_id UUID PRIMARY KEY,
       job_no VARCHAR(64) NOT NULL,
       job_state VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
       actual_start TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
       actual_end TIMESTAMP);
CREATE TABLE IF NOT EXISTS emc_job_response_data (
       id UUID PRIMARY KEY,
       response_id UUID NOT NULL,
       data_kind VARCHAR(32) NOT NULL,
       param_key VARCHAR(64),
       param_value VARCHAR(256),
       uom VARCHAR(16),
       started_at TIMESTAMP,
       ended_at TIMESTAMP,
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS emc_material_actual (
       id UUID PRIMARY KEY,
       response_id UUID NOT NULL,
       lot_id VARCHAR(64),
       sublot_id VARCHAR(64),
       definition_id VARCHAR(64),
       material_use VARCHAR(32) NOT NULL,
       quantity NUMERIC(14,3) NOT NULL DEFAULT 0,
       uom VARCHAR(16),
       recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS emc_equipment_actual (
       id UUID PRIMARY KEY,
       response_id UUID NOT NULL,
       equipment_id VARCHAR(64) NOT NULL,
       equipment_use VARCHAR(32),
       recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS emc_personnel_actual (
       id UUID PRIMARY KEY,
       response_id UUID NOT NULL,
       person_id VARCHAR(64) NOT NULL,
       personnel_use VARCHAR(32),
       recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS emc_lot_genealogy (
       id UUID PRIMARY KEY,
       input_lot_id VARCHAR(64) NOT NULL,
       output_lot_id VARCHAR(64) NOT NULL,
       response_id UUID,
       quantity NUMERIC(14,3),
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
INSERT INTO emc_job_response (response_id, job_no, job_state, actual_start)
       SELECT 'b0000002-0000-0000-0000-000000000002', 'JO-DEMO-002', 'RUNNING', CURRENT_TIMESTAMP
       WHERE NOT EXISTS (SELECT 1 FROM emc_job_response WHERE job_no = 'JO-DEMO-002' AND job_state = 'RUNNING');
INSERT INTO emc_job_response_data (id, response_id, data_kind, started_at)
       SELECT gen_random_uuid(), 'b0000002-0000-0000-0000-000000000002', 'RUN_INTERVAL', CURRENT_TIMESTAMP
       WHERE NOT EXISTS (SELECT 1 FROM emc_job_response_data WHERE response_id = 'b0000002-0000-0000-0000-000000000002' AND data_kind = 'RUN_INTERVAL' AND ended_at IS NULL);
INSERT INTO emc_equipment_actual (id, response_id, equipment_id, equipment_use)
       SELECT gen_random_uuid(), 'b0000002-0000-0000-0000-000000000002', 'WU-A01', 'PRIMARY'
       WHERE NOT EXISTS (SELECT 1 FROM emc_equipment_actual WHERE response_id = 'b0000002-0000-0000-0000-000000000002');
INSERT INTO emc_personnel_actual (id, response_id, person_id, personnel_use)
       SELECT gen_random_uuid(), 'b0000002-0000-0000-0000-000000000002', 'EMP-001', 'OPERATOR'
       WHERE NOT EXISTS (SELECT 1 FROM emc_personnel_actual WHERE response_id = 'b0000002-0000-0000-0000-000000000002')
