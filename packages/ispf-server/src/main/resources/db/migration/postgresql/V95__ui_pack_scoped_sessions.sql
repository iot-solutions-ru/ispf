-- ADR-0073: NULL ui_pack_app_id is a normal console session.
-- A non-null value is a BFF-scoped token for that hosted ui-pack.
ALTER TABLE platform_auth_sessions
    ADD COLUMN IF NOT EXISTS ui_pack_app_id VARCHAR(64);
