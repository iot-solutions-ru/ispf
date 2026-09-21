CREATE TABLE IF NOT EXISTS emc_operational_location (
       location_id VARCHAR(64) PRIMARY KEY,
       description VARCHAR(256),
       location_kind VARCHAR(32) NOT NULL DEFAULT 'STORAGE',
       equipment_id VARCHAR(64),
       parent_location_id VARCHAR(64),
       status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE');
CREATE TABLE IF NOT EXISTS emc_domain_schedule (
       schedule_id VARCHAR(64) PRIMARY KEY,
       domain VARCHAR(32) NOT NULL,
       schedule_kind VARCHAR(64) NOT NULL,
       target_id VARCHAR(64),
       planned_start TIMESTAMP,
       planned_end TIMESTAMP,
       quantity NUMERIC(14,3),
       uom VARCHAR(16),
       status VARCHAR(32) NOT NULL DEFAULT 'PLANNED',
       note VARCHAR(256),
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
INSERT INTO emc_operational_location (location_id, description, location_kind, equipment_id, parent_location_id, status) SELECT 'LOC-WH-RAW', 'Raw material warehouse', 'STORAGE', 'WH-CENTRAL', NULL, 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM emc_operational_location WHERE location_id = 'LOC-WH-RAW');
INSERT INTO emc_operational_location (location_id, description, location_kind, equipment_id, parent_location_id, status) SELECT 'LOC-WH-FG', 'Finished goods warehouse', 'STORAGE', 'WH-CENTRAL', NULL, 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM emc_operational_location WHERE location_id = 'LOC-WH-FG');
INSERT INTO emc_operational_location (location_id, description, location_kind, equipment_id, parent_location_id, status) SELECT 'LOC-LINE-A01', 'Line A01 staging', 'STAGING', 'WU-A01', 'LOC-WH-RAW', 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM emc_operational_location WHERE location_id = 'LOC-LINE-A01');
INSERT INTO emc_domain_schedule (schedule_id, domain, schedule_kind, target_id, quantity, uom, status, note) SELECT 'DS-QA-SAMPLE-001', 'QUALITY', 'SAMPLE_PLAN', 'LOT-FG-0001', '5', 'pcs', 'PLANNED', 'Incoming inspection sample plan' WHERE NOT EXISTS (SELECT 1 FROM emc_domain_schedule WHERE schedule_id = 'DS-QA-SAMPLE-001');
INSERT INTO emc_domain_schedule (schedule_id, domain, schedule_kind, target_id, quantity, uom, status, note) SELECT 'DS-INV-REPL-001', 'INVENTORY', 'REPLENISHMENT', 'RAW-PLASTIC-GRANULE', '100', 'kg', 'PLANNED', 'Min/max replenishment for plastic granulate' WHERE NOT EXISTS (SELECT 1 FROM emc_domain_schedule WHERE schedule_id = 'DS-INV-REPL-001');
INSERT INTO emc_domain_schedule (schedule_id, domain, schedule_kind, target_id, quantity, uom, status, note) SELECT 'DS-PM-A01-001', 'MAINTENANCE', 'PM_CALENDAR', 'WU-A01', '1', 'job', 'PLANNED', 'Monthly PM for assembly cell' WHERE NOT EXISTS (SELECT 1 FROM emc_domain_schedule WHERE schedule_id = 'DS-PM-A01-001');
INSERT INTO emc_domain_schedule (schedule_id, domain, schedule_kind, target_id, quantity, uom, status, note) SELECT 'DS-PROD-FIRM-001', 'PRODUCTION', 'FIRM_SCHEDULE', 'SCH-DEMO-001', '1', 'schedule', 'RELEASED', 'Link to work schedule SCH-DEMO-001' WHERE NOT EXISTS (SELECT 1 FROM emc_domain_schedule WHERE schedule_id = 'DS-PROD-FIRM-001');
UPDATE emc_mom_activity SET status = 'COVERED', note = 'Process segment + work master', ui_link = 'emc-dispatch' WHERE domain = 'PRODUCTION' AND activity = 'DEFINITION';
UPDATE emc_mom_activity SET status = 'COVERED', note = 'Equipment / personnel / material', ui_link = 'emc-inventory' WHERE domain = 'PRODUCTION' AND activity = 'RESOURCE';
UPDATE emc_mom_activity SET status = 'COVERED', note = 'Work schedule receive + domain schedule', ui_link = 'emc-dispatch' WHERE domain = 'PRODUCTION' AND activity = 'DETAILED_SCHEDULING';
UPDATE emc_mom_activity SET status = 'COVERED', note = 'Release / start / pause / resume', ui_link = 'emc-dispatch' WHERE domain = 'PRODUCTION' AND activity = 'DISPATCHING';
UPDATE emc_mom_activity SET status = 'COVERED', note = 'Job order lifecycle', ui_link = 'emc-execution' WHERE domain = 'PRODUCTION' AND activity = 'EXECUTION';
UPDATE emc_mom_activity SET status = 'COVERED', note = 'PDC + material actuals', ui_link = 'emc-execution' WHERE domain = 'PRODUCTION' AND activity = 'DATA_COLLECTION';
UPDATE emc_mom_activity SET status = 'COVERED', note = 'Lot genealogy tree', ui_link = 'emc-genealogy' WHERE domain = 'PRODUCTION' AND activity = 'TRACKING';
UPDATE emc_mom_activity SET status = 'COVERED', note = 'OEE A×P×Q', ui_link = 'emc-oee' WHERE domain = 'PRODUCTION' AND activity = 'PERFORMANCE_ANALYSIS';
UPDATE emc_mom_activity SET status = 'COVERED', note = 'Defect types / reason codes', ui_link = 'emc-quality' WHERE domain = 'QUALITY' AND activity = 'DEFINITION';
UPDATE emc_mom_activity SET status = 'COVERED', note = 'QA personnel via person catalog', ui_link = 'emc-quality' WHERE domain = 'QUALITY' AND activity = 'RESOURCE';
UPDATE emc_mom_activity SET status = 'COVERED', note = 'QA sample plan (domain schedule)', ui_link = 'emc-quality' WHERE domain = 'QUALITY' AND activity = 'DETAILED_SCHEDULING';
UPDATE emc_mom_activity SET status = 'COVERED', note = 'Defect workflow dispatch', ui_link = 'emc-quality' WHERE domain = 'QUALITY' AND activity = 'DISPATCHING';
UPDATE emc_mom_activity SET status = 'COVERED', note = 'Confirm / reject / close defect', ui_link = 'emc-quality' WHERE domain = 'QUALITY' AND activity = 'EXECUTION';
UPDATE emc_mom_activity SET status = 'COVERED', note = 'QA test results', ui_link = 'emc-quality' WHERE domain = 'QUALITY' AND activity = 'DATA_COLLECTION';
UPDATE emc_mom_activity SET status = 'COVERED', note = 'Defect status history', ui_link = 'emc-quality' WHERE domain = 'QUALITY' AND activity = 'TRACKING';
UPDATE emc_mom_activity SET status = 'COVERED', note = 'Defect rate KPI', ui_link = 'emc-mom-matrix' WHERE domain = 'QUALITY' AND activity = 'PERFORMANCE_ANALYSIS';
UPDATE emc_mom_activity SET status = 'COVERED', note = 'Inventory document kinds', ui_link = 'emc-inventory' WHERE domain = 'INVENTORY' AND activity = 'DEFINITION';
UPDATE emc_mom_activity SET status = 'COVERED', note = 'Storage zones + operational locations', ui_link = 'emc-inventory' WHERE domain = 'INVENTORY' AND activity = 'RESOURCE';
UPDATE emc_mom_activity SET status = 'COVERED', note = 'Replenishment schedule', ui_link = 'emc-inventory' WHERE domain = 'INVENTORY' AND activity = 'DETAILED_SCHEDULING';
UPDATE emc_mom_activity SET status = 'COVERED', note = 'Submit inventory document', ui_link = 'emc-inventory' WHERE domain = 'INVENTORY' AND activity = 'DISPATCHING';
UPDATE emc_mom_activity SET status = 'COVERED', note = 'Apply inventory document', ui_link = 'emc-inventory' WHERE domain = 'INVENTORY' AND activity = 'EXECUTION';
UPDATE emc_mom_activity SET status = 'COVERED', note = 'Document lines / stock qty', ui_link = 'emc-inventory' WHERE domain = 'INVENTORY' AND activity = 'DATA_COLLECTION';
UPDATE emc_mom_activity SET status = 'COVERED', note = 'Stock + material movement', ui_link = 'emc-inventory' WHERE domain = 'INVENTORY' AND activity = 'TRACKING';
UPDATE emc_mom_activity SET status = 'COVERED', note = 'Inventory turns KPI', ui_link = 'emc-mom-matrix' WHERE domain = 'INVENTORY' AND activity = 'PERFORMANCE_ANALYSIS';
UPDATE emc_mom_activity SET status = 'COVERED', note = 'Maintenance request model', ui_link = 'emc-oee' WHERE domain = 'MAINTENANCE' AND activity = 'DEFINITION';
UPDATE emc_mom_activity SET status = 'COVERED', note = 'Equipment as maint target', ui_link = 'emc-oee' WHERE domain = 'MAINTENANCE' AND activity = 'RESOURCE';
UPDATE emc_mom_activity SET status = 'COVERED', note = 'PM calendar (domain schedule)', ui_link = 'emc-oee' WHERE domain = 'MAINTENANCE' AND activity = 'DETAILED_SCHEDULING';
UPDATE emc_mom_activity SET status = 'COVERED', note = 'Accept maintenance request', ui_link = 'emc-oee' WHERE domain = 'MAINTENANCE' AND activity = 'DISPATCHING';
UPDATE emc_mom_activity SET status = 'COVERED', note = 'Complete work order', ui_link = 'emc-oee' WHERE domain = 'MAINTENANCE' AND activity = 'EXECUTION';
UPDATE emc_mom_activity SET status = 'COVERED', note = 'Event / downtime capture', ui_link = 'emc-oee' WHERE domain = 'MAINTENANCE' AND activity = 'DATA_COLLECTION';
UPDATE emc_mom_activity SET status = 'COVERED', note = 'Maintenance list', ui_link = 'emc-oee' WHERE domain = 'MAINTENANCE' AND activity = 'TRACKING';
UPDATE emc_mom_activity SET status = 'COVERED', note = 'MTTR / MTBF', ui_link = 'emc-mom-matrix' WHERE domain = 'MAINTENANCE' AND activity = 'PERFORMANCE_ANALYSIS';
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'PRODUCTION', 'DEFINITION', 'COVERED', 'Process segment + work master', 'emc-dispatch' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'PRODUCTION' AND activity = 'DEFINITION');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'PRODUCTION', 'RESOURCE', 'COVERED', 'Equipment / personnel / material', 'emc-inventory' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'PRODUCTION' AND activity = 'RESOURCE');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'PRODUCTION', 'DETAILED_SCHEDULING', 'COVERED', 'Work schedule receive + domain schedule', 'emc-dispatch' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'PRODUCTION' AND activity = 'DETAILED_SCHEDULING');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'PRODUCTION', 'DISPATCHING', 'COVERED', 'Release / start / pause / resume', 'emc-dispatch' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'PRODUCTION' AND activity = 'DISPATCHING');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'PRODUCTION', 'EXECUTION', 'COVERED', 'Job order lifecycle', 'emc-execution' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'PRODUCTION' AND activity = 'EXECUTION');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'PRODUCTION', 'DATA_COLLECTION', 'COVERED', 'PDC + material actuals', 'emc-execution' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'PRODUCTION' AND activity = 'DATA_COLLECTION');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'PRODUCTION', 'TRACKING', 'COVERED', 'Lot genealogy tree', 'emc-genealogy' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'PRODUCTION' AND activity = 'TRACKING');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'PRODUCTION', 'PERFORMANCE_ANALYSIS', 'COVERED', 'OEE A×P×Q', 'emc-oee' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'PRODUCTION' AND activity = 'PERFORMANCE_ANALYSIS');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'QUALITY', 'DEFINITION', 'COVERED', 'Defect types / reason codes', 'emc-quality' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'QUALITY' AND activity = 'DEFINITION');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'QUALITY', 'RESOURCE', 'COVERED', 'QA personnel via person catalog', 'emc-quality' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'QUALITY' AND activity = 'RESOURCE');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'QUALITY', 'DETAILED_SCHEDULING', 'COVERED', 'QA sample plan (domain schedule)', 'emc-quality' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'QUALITY' AND activity = 'DETAILED_SCHEDULING');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'QUALITY', 'DISPATCHING', 'COVERED', 'Defect workflow dispatch', 'emc-quality' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'QUALITY' AND activity = 'DISPATCHING');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'QUALITY', 'EXECUTION', 'COVERED', 'Confirm / reject / close defect', 'emc-quality' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'QUALITY' AND activity = 'EXECUTION');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'QUALITY', 'DATA_COLLECTION', 'COVERED', 'QA test results', 'emc-quality' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'QUALITY' AND activity = 'DATA_COLLECTION');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'QUALITY', 'TRACKING', 'COVERED', 'Defect status history', 'emc-quality' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'QUALITY' AND activity = 'TRACKING');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'QUALITY', 'PERFORMANCE_ANALYSIS', 'COVERED', 'Defect rate KPI', 'emc-mom-matrix' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'QUALITY' AND activity = 'PERFORMANCE_ANALYSIS');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'INVENTORY', 'DEFINITION', 'COVERED', 'Inventory document kinds', 'emc-inventory' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'INVENTORY' AND activity = 'DEFINITION');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'INVENTORY', 'RESOURCE', 'COVERED', 'Storage zones + operational locations', 'emc-inventory' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'INVENTORY' AND activity = 'RESOURCE');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'INVENTORY', 'DETAILED_SCHEDULING', 'COVERED', 'Replenishment schedule', 'emc-inventory' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'INVENTORY' AND activity = 'DETAILED_SCHEDULING');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'INVENTORY', 'DISPATCHING', 'COVERED', 'Submit inventory document', 'emc-inventory' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'INVENTORY' AND activity = 'DISPATCHING');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'INVENTORY', 'EXECUTION', 'COVERED', 'Apply inventory document', 'emc-inventory' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'INVENTORY' AND activity = 'EXECUTION');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'INVENTORY', 'DATA_COLLECTION', 'COVERED', 'Document lines / stock qty', 'emc-inventory' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'INVENTORY' AND activity = 'DATA_COLLECTION');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'INVENTORY', 'TRACKING', 'COVERED', 'Stock + material movement', 'emc-inventory' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'INVENTORY' AND activity = 'TRACKING');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'INVENTORY', 'PERFORMANCE_ANALYSIS', 'COVERED', 'Inventory turns KPI', 'emc-mom-matrix' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'INVENTORY' AND activity = 'PERFORMANCE_ANALYSIS');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'MAINTENANCE', 'DEFINITION', 'COVERED', 'Maintenance request model', 'emc-oee' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'MAINTENANCE' AND activity = 'DEFINITION');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'MAINTENANCE', 'RESOURCE', 'COVERED', 'Equipment as maint target', 'emc-oee' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'MAINTENANCE' AND activity = 'RESOURCE');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'MAINTENANCE', 'DETAILED_SCHEDULING', 'COVERED', 'PM calendar (domain schedule)', 'emc-oee' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'MAINTENANCE' AND activity = 'DETAILED_SCHEDULING');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'MAINTENANCE', 'DISPATCHING', 'COVERED', 'Accept maintenance request', 'emc-oee' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'MAINTENANCE' AND activity = 'DISPATCHING');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'MAINTENANCE', 'EXECUTION', 'COVERED', 'Complete work order', 'emc-oee' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'MAINTENANCE' AND activity = 'EXECUTION');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'MAINTENANCE', 'DATA_COLLECTION', 'COVERED', 'Event / downtime capture', 'emc-oee' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'MAINTENANCE' AND activity = 'DATA_COLLECTION');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'MAINTENANCE', 'TRACKING', 'COVERED', 'Maintenance list', 'emc-oee' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'MAINTENANCE' AND activity = 'TRACKING');
INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) SELECT 'MAINTENANCE', 'PERFORMANCE_ANALYSIS', 'COVERED', 'MTTR / MTBF', 'emc-mom-matrix' WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = 'MAINTENANCE' AND activity = 'PERFORMANCE_ANALYSIS')
