CREATE TABLE IF NOT EXISTS emc_work_record (
       record_id VARCHAR(64) PRIMARY KEY,
       job_no VARCHAR(64) NOT NULL UNIQUE,
       record_no VARCHAR(64) NOT NULL,
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS emc_work_record_section (
       record_id VARCHAR(64) NOT NULL,
       section_key VARCHAR(64) NOT NULL,
       title VARCHAR(256),
       content_json VARCHAR(8192),
       updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
INSERT INTO emc_work_record (record_id, job_no, record_no) SELECT 'WR-JO-DEMO-002', 'JO-DEMO-002', 'WREC-JO-DEMO-002' WHERE NOT EXISTS (SELECT 1 FROM emc_work_record WHERE record_id = 'WR-JO-DEMO-002');
INSERT INTO emc_work_record_section (record_id, section_key, title, content_json)
       SELECT 'WR-JO-DEMO-002', 'params', 'Process parameters', '{"temperature":"210","pressure":"40"}'
       WHERE NOT EXISTS (SELECT 1 FROM emc_work_record_section WHERE record_id = 'WR-JO-DEMO-002' AND section_key = 'params');
INSERT INTO emc_work_record_section (record_id, section_key, title, content_json)
       SELECT 'WR-JO-DEMO-002', 'checklist', 'Start checklist', '{"guardsClosed":"true","materialsStaged":"false"}'
       WHERE NOT EXISTS (SELECT 1 FROM emc_work_record_section WHERE record_id = 'WR-JO-DEMO-002' AND section_key = 'checklist')
