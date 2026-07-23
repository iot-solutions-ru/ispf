-- Rename blueprint catalogs and types: RELATIVE→MIXIN, ABSOLUTE→SINGLETON.

-- Tree catalog paths (and children)
UPDATE object_nodes
SET path = REPLACE(path, 'relative-blueprints', 'mixin-blueprints')
WHERE path LIKE '%relative-blueprints%';

UPDATE object_nodes
SET path = REPLACE(path, 'absolute-blueprints', 'singleton-blueprints')
WHERE path LIKE '%absolute-blueprints%';

UPDATE object_nodes
SET display_name = 'Mixin Blueprints'
WHERE path = 'root.platform.mixin-blueprints';

UPDATE object_nodes
SET display_name = 'Singleton Blueprints'
WHERE path = 'root.platform.singleton-blueprints';

-- Dependent path columns
UPDATE object_variables
SET object_path = REPLACE(object_path, 'relative-blueprints', 'mixin-blueprints')
WHERE object_path LIKE '%relative-blueprints%';

UPDATE object_variables
SET object_path = REPLACE(object_path, 'absolute-blueprints', 'singleton-blueprints')
WHERE object_path LIKE '%absolute-blueprints%';

UPDATE object_acl_entries
SET object_path = REPLACE(object_path, 'relative-blueprints', 'mixin-blueprints')
WHERE object_path LIKE '%relative-blueprints%';

UPDATE object_acl_entries
SET object_path = REPLACE(object_path, 'absolute-blueprints', 'singleton-blueprints')
WHERE object_path LIKE '%absolute-blueprints%';

-- Blueprint definition JSON: type enum + singleton path parameter key
UPDATE blueprint_definitions
SET definition_json = REPLACE(definition_json, '"type":"RELATIVE"', '"type":"MIXIN"');

UPDATE blueprint_definitions
SET definition_json = REPLACE(definition_json, '"type": "RELATIVE"', '"type": "MIXIN"');

UPDATE blueprint_definitions
SET definition_json = REPLACE(definition_json, '"type":"ABSOLUTE"', '"type":"SINGLETON"');

UPDATE blueprint_definitions
SET definition_json = REPLACE(definition_json, '"type": "ABSOLUTE"', '"type": "SINGLETON"');

UPDATE blueprint_definitions
SET definition_json = REPLACE(definition_json, 'absoluteInstancePath', 'singletonInstancePath');
