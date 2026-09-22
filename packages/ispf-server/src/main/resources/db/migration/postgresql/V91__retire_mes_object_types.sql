-- MES catalogs are bundle-owned CUSTOM nodes under root.platform.mes.*.
-- Same retirement as V80 (QUERY -> CUSTOM). No runtime read of the old names.
UPDATE object_nodes
SET type = 'CUSTOM'
WHERE type IN (
    'MES',
    'WORK_ORDERS',
    'WORK_ORDER',
    'OPERATIONS',
    'OPERATION',
    'LOTS',
    'LOT',
    'SHIFTS',
    'SHIFT',
    'QUALITY_RECORDS',
    'QUALITY_RECORD',
    'MES_INSTANCES'
);

UPDATE blueprint_definitions
SET definition_json = regexp_replace(
    definition_json,
    '"targetObjectType"[[:space:]]*:[[:space:]]*"(MES|WORK_ORDERS|WORK_ORDER|OPERATIONS|OPERATION|LOTS|LOT|SHIFTS|SHIFT|QUALITY_RECORDS|QUALITY_RECORD|MES_INSTANCES)"',
    '"targetObjectType":"CUSTOM"',
    'g'
);
