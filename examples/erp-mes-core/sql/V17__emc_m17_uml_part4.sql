CREATE TABLE IF NOT EXISTS emc_work_master_node (
       node_id VARCHAR(64) PRIMARY KEY,
       work_master_id VARCHAR(64) NOT NULL,
       version VARCHAR(16) NOT NULL DEFAULT '1',
       segment_id VARCHAR(64) NOT NULL,
       sequence_no INTEGER NOT NULL DEFAULT 1,
       node_kind VARCHAR(32) NOT NULL DEFAULT 'SEGMENT');
CREATE TABLE IF NOT EXISTS emc_work_master_edge (
       edge_id VARCHAR(64) PRIMARY KEY,
       work_master_id VARCHAR(64) NOT NULL,
       version VARCHAR(16) NOT NULL DEFAULT '1',
       from_node_id VARCHAR(64) NOT NULL,
       to_node_id VARCHAR(64) NOT NULL,
       edge_kind VARCHAR(32) NOT NULL DEFAULT 'SEQUENCE');
CREATE TABLE IF NOT EXISTS emc_job_order_parameter_req (
       job_no VARCHAR(64) NOT NULL,
       param_key VARCHAR(64) NOT NULL,
       param_value VARCHAR(128),
       uom VARCHAR(32),
       PRIMARY KEY (job_no, param_key));
CREATE TABLE IF NOT EXISTS emc_work_directive (
       directive_id VARCHAR(64) PRIMARY KEY,
       work_master_id VARCHAR(64),
       version VARCHAR(16),
       job_no VARCHAR(64),
       title VARCHAR(256) NOT NULL,
       body_text VARCHAR(2048),
       status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS emc_work_performance (
       performance_id VARCHAR(64) PRIMARY KEY,
       job_no VARCHAR(64),
       work_master_id VARCHAR(64),
       start_time TIMESTAMP,
       end_time TIMESTAMP,
       good_qty NUMERIC(14,3) NOT NULL DEFAULT 0,
       reject_qty NUMERIC(14,3) NOT NULL DEFAULT 0,
       status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
       note VARCHAR(256),
       calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS emc_genealogy_node (
       node_id VARCHAR(64) PRIMARY KEY,
       node_kind VARCHAR(32) NOT NULL DEFAULT 'LOT',
       lot_id VARCHAR(64),
       definition_id VARCHAR(64),
       label VARCHAR(256));
ALTER TABLE emc_lot_genealogy ADD COLUMN IF NOT EXISTS relation_kind VARCHAR(32);
ALTER TABLE emc_lot_genealogy ADD COLUMN IF NOT EXISTS assembly_type VARCHAR(32);
UPDATE emc_lot_genealogy SET relation_kind = 'CONSUME_PRODUCE' WHERE relation_kind IS NULL;
UPDATE emc_lot_genealogy SET assembly_type = 'PROCESS' WHERE assembly_type IS NULL;
INSERT INTO emc_work_master (work_master_id, version, segment_id, duration_min, description) SELECT 'WM-ROUTE-PACK', '1', 'SEG-ASSEMBLE', '90', 'Assemble then pack (multi-segment)' WHERE NOT EXISTS (SELECT 1 FROM emc_work_master WHERE work_master_id = 'WM-ROUTE-PACK' AND version = '1');
INSERT INTO emc_work_master_node (node_id, work_master_id, version, segment_id, sequence_no, node_kind) SELECT 'WMN-ASSEMBLE-1', 'WM-ASSEMBLE', '1', 'SEG-ASSEMBLE', '1', 'SEGMENT' WHERE NOT EXISTS (SELECT 1 FROM emc_work_master_node WHERE node_id = 'WMN-ASSEMBLE-1');
INSERT INTO emc_work_master_node (node_id, work_master_id, version, segment_id, sequence_no, node_kind) SELECT 'WMN-ROUTE-A', 'WM-ROUTE-PACK', '1', 'SEG-ASSEMBLE', '1', 'SEGMENT' WHERE NOT EXISTS (SELECT 1 FROM emc_work_master_node WHERE node_id = 'WMN-ROUTE-A');
INSERT INTO emc_work_master_node (node_id, work_master_id, version, segment_id, sequence_no, node_kind) SELECT 'WMN-ROUTE-P', 'WM-ROUTE-PACK', '1', 'SEG-PACK', '2', 'SEGMENT' WHERE NOT EXISTS (SELECT 1 FROM emc_work_master_node WHERE node_id = 'WMN-ROUTE-P');
INSERT INTO emc_work_master_edge (edge_id, work_master_id, version, from_node_id, to_node_id, edge_kind) SELECT 'WME-ROUTE-AP', 'WM-ROUTE-PACK', '1', 'WMN-ROUTE-A', 'WMN-ROUTE-P', 'SEQUENCE' WHERE NOT EXISTS (SELECT 1 FROM emc_work_master_edge WHERE edge_id = 'WME-ROUTE-AP');
INSERT INTO emc_job_order_parameter_req (job_no, param_key, param_value, uom) SELECT 'JO-DEMO-002', 'TEMPERATURE', '210', 'C' WHERE NOT EXISTS (SELECT 1 FROM emc_job_order_parameter_req WHERE job_no = 'JO-DEMO-002' AND param_key = 'TEMPERATURE');
INSERT INTO emc_work_directive (directive_id, work_master_id, version, job_no, title, body_text, status) SELECT 'WD-ASSEMBLE-1', 'WM-ASSEMBLE', '1', 'JO-DEMO-002', 'Assembly line clearance', 'Verify guards closed; materials staged; start checklist OK.', 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM emc_work_directive WHERE directive_id = 'WD-ASSEMBLE-1');
INSERT INTO emc_work_performance (performance_id, job_no, work_master_id, good_qty, reject_qty, status, note) SELECT 'WP-JO-DEMO-002', 'JO-DEMO-002', 'WM-ASSEMBLE', '0', '0', 'OPEN', 'Seed performance header' WHERE NOT EXISTS (SELECT 1 FROM emc_work_performance WHERE performance_id = 'WP-JO-DEMO-002');
INSERT INTO emc_genealogy_node (node_id, node_kind, lot_id, definition_id, label) SELECT 'GN-LOT-FG-0001', 'LOT', 'LOT-FG-0001', 'FG-UNIT-PACKED', 'Finished unit lot' WHERE NOT EXISTS (SELECT 1 FROM emc_genealogy_node WHERE node_id = 'GN-LOT-FG-0001');
INSERT INTO emc_material_sublot (sublot_id, lot_id, barcode, status, storage_location, quantity) SELECT 'SL-RAW-0001-A', 'LOT-RAW-0001', 'BC-RAW-0001-A', 'STOCK', 'WH-LINE-A01', '100' WHERE NOT EXISTS (SELECT 1 FROM emc_material_sublot WHERE sublot_id = 'SL-RAW-0001-A')
