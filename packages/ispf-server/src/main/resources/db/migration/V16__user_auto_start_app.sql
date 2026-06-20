ALTER TABLE platform_users
    ADD COLUMN auto_start_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE platform_users
    ADD COLUMN auto_start_app VARCHAR(128);
