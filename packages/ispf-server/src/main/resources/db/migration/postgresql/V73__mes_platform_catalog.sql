-- BL-164: reconcile MES catalog sort order (folders seeded by PlatformBootstrap / MesPlatformBootstrap)

UPDATE object_nodes SET sort_order = 12 WHERE path = 'root.platform.schedules';
UPDATE object_nodes SET sort_order = 13 WHERE path = 'root.platform.data-sources';
UPDATE object_nodes SET sort_order = 14 WHERE path = 'root.platform.bindings';
UPDATE object_nodes SET sort_order = 15 WHERE path = 'root.platform.migrations';
UPDATE object_nodes SET sort_order = 16 WHERE path = 'root.platform.federation';
UPDATE object_nodes SET sort_order = 17 WHERE path = 'root.platform.applications';
UPDATE object_nodes SET sort_order = 18 WHERE path = 'root.platform.instances';

UPDATE object_nodes SET sort_order = 11 WHERE path = 'root.platform.mes';
