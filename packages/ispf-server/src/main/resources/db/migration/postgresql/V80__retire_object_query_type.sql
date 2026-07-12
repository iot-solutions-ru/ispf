-- Retire legacy ObjectType.QUERY catalog instances (no runtime backward compatibility).
UPDATE object_nodes
SET type = 'CUSTOM',
    template_id = NULL
WHERE type = 'QUERY';

UPDATE object_nodes
SET type = 'CUSTOM',
    template_id = NULL
WHERE template_id = 'query-v1';
