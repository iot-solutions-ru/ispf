CREATE TABLE IF NOT EXISTS emc_work_schedule (
       schedule_id VARCHAR(64) PRIMARY KEY,
       external_ref VARCHAR(128),
       schedule_state VARCHAR(32) NOT NULL DEFAULT 'FIRM',
       start_time TIMESTAMP,
       end_time TIMESTAMP,
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS emc_work_request (
       request_id VARCHAR(64) PRIMARY KEY,
       schedule_id VARCHAR(64) NOT NULL,
       request_state VARCHAR(32) NOT NULL DEFAULT 'ACCEPTED',
       priority INTEGER NOT NULL DEFAULT 5,
       product_definition_id VARCHAR(64),
       quantity NUMERIC(14,3) NOT NULL DEFAULT 0,
       uom VARCHAR(16),
       start_time TIMESTAMP,
       end_time TIMESTAMP);
CREATE TABLE IF NOT EXISTS emc_job_order (
       job_order_id UUID PRIMARY KEY,
       job_no VARCHAR(64) NOT NULL UNIQUE,
       request_id VARCHAR(64) NOT NULL,
       work_master_id VARCHAR(64),
       work_master_version VARCHAR(16),
       segment_id VARCHAR(64),
       equipment_id VARCHAR(64) NOT NULL,
       dispatch_status VARCHAR(32) NOT NULL DEFAULT 'NOT_ALLOWED',
       command VARCHAR(32),
       priority INTEGER NOT NULL DEFAULT 5,
       planned_start TIMESTAMP,
       planned_end TIMESTAMP,
       actual_start TIMESTAMP,
       actual_end TIMESTAMP,
       original_job_no VARCHAR(64),
       replaced_by_job_no VARCHAR(64),
       replan_reason_code VARCHAR(64),
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS emc_job_order_material_req (
       job_no VARCHAR(64) NOT NULL,
       definition_id VARCHAR(64),
       material_class_id VARCHAR(64),
       material_use VARCHAR(32) NOT NULL,
       quantity NUMERIC(14,3) NOT NULL DEFAULT 0,
       uom VARCHAR(16));
CREATE TABLE IF NOT EXISTS emc_job_order_equipment_req (
       job_no VARCHAR(64) NOT NULL,
       equipment_class_id VARCHAR(64),
       equipment_id VARCHAR(64),
       equipment_use VARCHAR(32),
       quantity NUMERIC(14,3) NOT NULL DEFAULT 1);
CREATE TABLE IF NOT EXISTS emc_job_order_personnel_req (
       job_no VARCHAR(64) NOT NULL,
       personnel_class_id VARCHAR(64),
       person_id VARCHAR(64),
       personnel_use VARCHAR(32),
       quantity NUMERIC(14,3) NOT NULL DEFAULT 1);
CREATE TABLE IF NOT EXISTS emc_job_order_audit (
       id UUID PRIMARY KEY,
       job_no VARCHAR(64) NOT NULL,
       action VARCHAR(64) NOT NULL,
       detail VARCHAR(1024),
       actor VARCHAR(64),
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
INSERT INTO emc_work_schedule (schedule_id, external_ref, schedule_state, start_time, end_time) SELECT 'SCH-DEMO-001', 'ERP-PO-1000456', 'RELEASED', TIMESTAMP '2026-07-24 00:00:00', TIMESTAMP '2026-07-25 00:00:00' WHERE NOT EXISTS (SELECT 1 FROM emc_work_schedule WHERE schedule_id = 'SCH-DEMO-001');
INSERT INTO emc_work_request (request_id, schedule_id, request_state, priority, product_definition_id, quantity, uom, start_time, end_time) SELECT 'WR-DEMO-001', 'SCH-DEMO-001', 'ACCEPTED', '1', 'FG-UNIT-PACKED', '100', 'pcs', TIMESTAMP '2026-07-24 06:00:00', TIMESTAMP '2026-07-24 18:00:00' WHERE NOT EXISTS (SELECT 1 FROM emc_work_request WHERE request_id = 'WR-DEMO-001');
INSERT INTO emc_job_order (job_order_id, job_no, request_id, work_master_id, work_master_version, segment_id, equipment_id, dispatch_status, command, priority, planned_start, planned_end, actual_start)
       SELECT 'a0000002-0000-0000-0000-000000000002', 'JO-DEMO-002', 'WR-DEMO-001', 'WM-ASSEMBLE', '1', 'SEG-ASSEMBLE', 'WU-A01', 'RUNNING', 'START', '1',
              TIMESTAMP '2026-07-24 06:00:00', TIMESTAMP '2026-07-24 08:00:00', CURRENT_TIMESTAMP
       WHERE NOT EXISTS (SELECT 1 FROM emc_job_order WHERE job_no = 'JO-DEMO-002');
INSERT INTO emc_job_order (job_order_id, job_no, request_id, work_master_id, work_master_version, segment_id, equipment_id, dispatch_status, command, priority, planned_start, planned_end)
       SELECT 'a0000001-0000-0000-0000-000000000001', 'JO-DEMO-001', 'WR-DEMO-001', 'WM-PACK', '1', 'SEG-PACK', 'WU-A02', 'ALLOWED', 'STORE', '1',
              TIMESTAMP '2026-07-24 08:00:00', TIMESTAMP '2026-07-24 10:00:00'
       WHERE NOT EXISTS (SELECT 1 FROM emc_job_order WHERE job_no = 'JO-DEMO-001');
INSERT INTO emc_job_order (job_order_id, job_no, request_id, work_master_id, work_master_version, segment_id, equipment_id, dispatch_status, command, priority, planned_start, planned_end)
       SELECT 'a0000003-0000-0000-0000-000000000003', 'JO-DEMO-003', 'WR-DEMO-001', 'WM-PACK', '1', 'SEG-PACK', 'WU-A02', 'ALLOWED', 'STORE', '2',
              TIMESTAMP '2026-07-24 10:00:00', TIMESTAMP '2026-07-24 12:00:00'
       WHERE NOT EXISTS (SELECT 1 FROM emc_job_order WHERE job_no = 'JO-DEMO-003');
INSERT INTO emc_job_order_material_req (job_no, definition_id, material_class_id, material_use, quantity, uom)
       SELECT 'JO-DEMO-002', definition_id, material_class_id, material_use, quantity, uom FROM emc_segment_material_spec
       WHERE segment_id = 'SEG-ASSEMBLE' AND NOT EXISTS (SELECT 1 FROM emc_job_order_material_req WHERE job_no = 'JO-DEMO-002');
INSERT INTO emc_job_order_equipment_req (job_no, equipment_class_id, equipment_id, equipment_use, quantity)
       SELECT 'JO-DEMO-002', equipment_class_id, equipment_id, equipment_use, quantity FROM emc_segment_equipment_spec
       WHERE segment_id = 'SEG-ASSEMBLE' AND NOT EXISTS (SELECT 1 FROM emc_job_order_equipment_req WHERE job_no = 'JO-DEMO-002');
INSERT INTO emc_job_order_personnel_req (job_no, personnel_class_id, person_id, personnel_use, quantity)
       SELECT 'JO-DEMO-002', personnel_class_id, person_id, personnel_use, quantity FROM emc_segment_personnel_spec
       WHERE segment_id = 'SEG-ASSEMBLE' AND NOT EXISTS (SELECT 1 FROM emc_job_order_personnel_req WHERE job_no = 'JO-DEMO-002')
