-- BL-184: persisted partner directory and enrollment applications

CREATE TABLE IF NOT EXISTS partner_directory (
    id                   VARCHAR(64)  PRIMARY KEY,
    name                 VARCHAR(256) NOT NULL,
    certification_level  VARCHAR(64)  NOT NULL,
    tier_id              VARCHAR(32)  NOT NULL,
    regions_json         TEXT,
    verticals_json       TEXT,
    marketplace_url      VARCHAR(512),
    certified_since      VARCHAR(16),
    status               VARCHAR(32)  NOT NULL DEFAULT 'certified'
);

CREATE INDEX IF NOT EXISTS idx_partner_directory_tier
    ON partner_directory (tier_id);

CREATE TABLE IF NOT EXISTS partner_enrollments (
    application_id  VARCHAR(64)  PRIMARY KEY,
    company_name    VARCHAR(256),
    contact_email   VARCHAR(256),
    tier_id         VARCHAR(32)  NOT NULL,
    verticals_json  TEXT,
    regions_json    TEXT,
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACCEPTED',
    created_at      TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_partner_enrollments_created_at
    ON partner_enrollments (created_at DESC);
