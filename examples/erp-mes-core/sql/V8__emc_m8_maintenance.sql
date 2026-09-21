CREATE TABLE IF NOT EXISTS emc_maintenance_request (
       request_id VARCHAR(64) PRIMARY KEY,
       equipment_id VARCHAR(64) NOT NULL,
       description VARCHAR(512),
       priority INTEGER NOT NULL DEFAULT 5,
       status VARCHAR(32) NOT NULL DEFAULT 'NEW',
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS emc_maintenance_work_order (
       wo_id VARCHAR(64) PRIMARY KEY,
       request_id VARCHAR(64),
       equipment_id VARCHAR(64) NOT NULL,
       status VARCHAR(32) NOT NULL DEFAULT 'PLANNED',
       planned_start TIMESTAMP,
       planned_end TIMESTAMP,
       actual_start TIMESTAMP,
       actual_end TIMESTAMP,
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
