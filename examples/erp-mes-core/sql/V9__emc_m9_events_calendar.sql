CREATE TABLE IF NOT EXISTS emc_operations_event_definition (
       code VARCHAR(64) PRIMARY KEY,
       event_class VARCHAR(32) NOT NULL DEFAULT 'DOWNTIME',
       name VARCHAR(256) NOT NULL,
       requires_length BOOLEAN NOT NULL DEFAULT false,
       requires_time BOOLEAN NOT NULL DEFAULT false,
       requires_comment BOOLEAN NOT NULL DEFAULT false,
       oee_bucket VARCHAR(32) NOT NULL DEFAULT 'NONE',
       six_big_loss VARCHAR(64),
       sort_order INTEGER NOT NULL DEFAULT 100);
CREATE TABLE IF NOT EXISTS emc_operations_event (
       event_id UUID PRIMARY KEY,
       definition_code VARCHAR(64) NOT NULL,
       job_no VARCHAR(64),
       equipment_id VARCHAR(64),
       lot_id VARCHAR(64),
       length_m NUMERIC(14,3),
       time_min NUMERIC(10,1),
       comment_text VARCHAR(1024),
       status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
       registered_by VARCHAR(64),
       started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
       ended_at TIMESTAMP);
CREATE TABLE IF NOT EXISTS emc_machine_signal (
       signal_id UUID PRIMARY KEY,
       equipment_id VARCHAR(64) NOT NULL,
       signal_code VARCHAR(64) NOT NULL,
       is_auto BOOLEAN NOT NULL DEFAULT false,
       is_resolved BOOLEAN NOT NULL DEFAULT false,
       received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS emc_work_calendar (
       shift_id VARCHAR(64) PRIMARY KEY,
       equipment_id VARCHAR(64) NOT NULL,
       shift_label VARCHAR(64) NOT NULL,
       planned_minutes NUMERIC(10,1) NOT NULL DEFAULT 480,
       state VARCHAR(32) NOT NULL DEFAULT 'OPEN',
       planned_start TIMESTAMP,
       actual_start TIMESTAMP,
       actual_end TIMESTAMP);
CREATE TABLE IF NOT EXISTS emc_shift_assignment (
       id UUID PRIMARY KEY,
       shift_id VARCHAR(64) NOT NULL,
       person_id VARCHAR(64) NOT NULL,
       handover_from_id VARCHAR(64),
       assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
INSERT INTO emc_operations_event_definition (code, event_class, name, requires_length, requires_time, requires_comment, oee_bucket, six_big_loss, sort_order) SELECT 'SETUP', 'SETUP', 'Changeover / setup', false, true, false, 'AVAILABILITY', 'SETUP_ADJUSTMENT', '10' WHERE NOT EXISTS (SELECT 1 FROM emc_operations_event_definition WHERE code = 'SETUP');
INSERT INTO emc_operations_event_definition (code, event_class, name, requires_length, requires_time, requires_comment, oee_bucket, six_big_loss, sort_order) SELECT 'BREAKDOWN', 'DOWNTIME', 'Equipment breakdown', false, true, true, 'AVAILABILITY', 'BREAKDOWN', '20' WHERE NOT EXISTS (SELECT 1 FROM emc_operations_event_definition WHERE code = 'BREAKDOWN');
INSERT INTO emc_operations_event_definition (code, event_class, name, requires_length, requires_time, requires_comment, oee_bucket, six_big_loss, sort_order) SELECT 'NO_MATERIAL', 'DOWNTIME', 'No material at line', false, true, false, 'AVAILABILITY', 'IDLING', '30' WHERE NOT EXISTS (SELECT 1 FROM emc_operations_event_definition WHERE code = 'NO_MATERIAL');
INSERT INTO emc_operations_event_definition (code, event_class, name, requires_length, requires_time, requires_comment, oee_bucket, six_big_loss, sort_order) SELECT 'SPEED_LOSS', 'OEE', 'Reduced speed run', false, true, false, 'PERFORMANCE', 'REDUCED_SPEED', '40' WHERE NOT EXISTS (SELECT 1 FROM emc_operations_event_definition WHERE code = 'SPEED_LOSS');
INSERT INTO emc_operations_event_definition (code, event_class, name, requires_length, requires_time, requires_comment, oee_bucket, six_big_loss, sort_order) SELECT 'QC_HOLD', 'QUALITY', 'Quality hold', false, true, true, 'AVAILABILITY', 'BREAKDOWN', '50' WHERE NOT EXISTS (SELECT 1 FROM emc_operations_event_definition WHERE code = 'QC_HOLD');
INSERT INTO emc_work_calendar (shift_id, equipment_id, shift_label, planned_minutes, state, planned_start, actual_start) SELECT 'SHIFT-DEMO-1', 'WU-A01', 'MORNING', '480', 'OPEN', TIMESTAMP '2026-07-24 06:00:00', CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM emc_work_calendar WHERE shift_id = 'SHIFT-DEMO-1');
INSERT INTO emc_shift_assignment (id, shift_id, person_id)
       SELECT gen_random_uuid(), 'SHIFT-DEMO-1', 'EMP-001'
       WHERE NOT EXISTS (SELECT 1 FROM emc_shift_assignment WHERE shift_id = 'SHIFT-DEMO-1' AND person_id = 'EMP-001')
