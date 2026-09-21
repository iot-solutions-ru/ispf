CREATE TABLE IF NOT EXISTS emc_inventory_document (
       doc_id VARCHAR(64) PRIMARY KEY,
       kind VARCHAR(32) NOT NULL,
       status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
       external_doc_ref VARCHAR(128),
       integration_response_code VARCHAR(64),
       integration_response_message VARCHAR(512),
       operator_person_id VARCHAR(64),
       version_no INTEGER NOT NULL DEFAULT 1,
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
       submitted_at TIMESTAMP,
       completed_at TIMESTAMP);
CREATE TABLE IF NOT EXISTS emc_inventory_document_line (
       line_id UUID PRIMARY KEY,
       doc_id VARCHAR(64) NOT NULL,
       definition_id VARCHAR(64),
       lot_id VARCHAR(64),
       quantity NUMERIC(14,3) NOT NULL DEFAULT 0,
       source_location VARCHAR(64),
       dest_location VARCHAR(64))
