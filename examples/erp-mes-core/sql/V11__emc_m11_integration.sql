CREATE TABLE IF NOT EXISTS emc_erp_outbox (
       id UUID PRIMARY KEY,
       verb VARCHAR(32) NOT NULL,
       noun VARCHAR(64) NOT NULL,
       object_id VARCHAR(128),
       payload_json VARCHAR(8192),
       idempotency_key VARCHAR(256) NOT NULL UNIQUE,
       status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
       ack_code VARCHAR(32),
       retry_count INTEGER NOT NULL DEFAULT 0,
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS emc_erp_inbox (
       id UUID PRIMARY KEY,
       verb VARCHAR(32) NOT NULL,
       noun VARCHAR(64) NOT NULL,
       payload_json VARCHAR(8192),
       idempotency_key VARCHAR(256) NOT NULL UNIQUE,
       status VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
       received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
       processed_at TIMESTAMP);
CREATE TABLE IF NOT EXISTS emc_integration_log (
       id UUID PRIMARY KEY,
       direction VARCHAR(16) NOT NULL,
       verb VARCHAR(32),
       noun VARCHAR(64),
       success BOOLEAN NOT NULL DEFAULT true,
       code VARCHAR(64),
       message VARCHAR(512),
       retryable BOOLEAN NOT NULL DEFAULT false,
       details_json VARCHAR(4096),
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS emc_master_data_replica (
       entity_type VARCHAR(64) NOT NULL,
       external_id VARCHAR(128) NOT NULL,
       payload_json VARCHAR(4096),
       synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
