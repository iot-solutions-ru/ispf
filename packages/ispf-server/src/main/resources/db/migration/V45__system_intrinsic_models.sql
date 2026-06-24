-- Remove system-intrinsic model catalog nodes (schemas are embedded in object instances).
DELETE FROM object_nodes
WHERE path IN (
    'root.platform.relative-models.data-source-v1',
    'root.platform.relative-models.schedule-v1',
    'root.platform.relative-models.sql-binding-v1',
    'root.platform.relative-models.migration-v1',
    'root.platform.relative-models.alert-rule-v1',
    'root.platform.relative-models.correlator-v1',
    'root.platform.relative-models.dashboard-v1',
    'root.platform.relative-models.report-v1',
    'root.platform.relative-models.workflow-v1'
);
