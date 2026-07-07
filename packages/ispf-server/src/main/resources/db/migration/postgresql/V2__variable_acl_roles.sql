-- Per-variable ACL: optional role lists (JSON arrays). Empty/null = inherit object ACL.

ALTER TABLE object_variables
    ADD COLUMN IF NOT EXISTS read_roles_json TEXT;

ALTER TABLE object_variables
    ADD COLUMN IF NOT EXISTS write_roles_json TEXT;
