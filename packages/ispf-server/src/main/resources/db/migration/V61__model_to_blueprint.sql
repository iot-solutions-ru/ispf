-- Rename model domain to blueprint (ObjectType, catalog paths, persistence).

UPDATE object_nodes SET type = 'BLUEPRINT' WHERE type = 'MODEL';

UPDATE object_nodes SET path = REPLACE(path, 'relative-models', 'relative-blueprints');
UPDATE object_nodes SET path = REPLACE(path, 'absolute-models', 'absolute-blueprints');
UPDATE object_nodes SET path = REPLACE(path, 'root.platform.models', 'root.platform.blueprints');

ALTER TABLE object_nodes ADD COLUMN IF NOT EXISTS applied_blueprint_ids TEXT;
UPDATE object_nodes
SET applied_blueprint_ids = applied_model_ids
WHERE applied_model_ids IS NOT NULL
  AND (applied_blueprint_ids IS NULL OR TRIM(applied_blueprint_ids) = '');
ALTER TABLE object_nodes DROP COLUMN IF EXISTS applied_model_ids;

ALTER TABLE model_definitions RENAME TO blueprint_definitions;

UPDATE blueprint_definitions
SET definition_json = REPLACE(definition_json, '"modelVersion"', '"blueprintVersion"');
