ALTER TABLE platform_cluster_replicas
    ADD COLUMN replica_profile VARCHAR(32) NOT NULL DEFAULT 'unified';

ALTER TABLE platform_cluster_replicas
    ADD COLUMN replica_capabilities VARCHAR(512) NOT NULL DEFAULT '';

UPDATE platform_cluster_replicas
SET replica_profile = CASE replica_role
    WHEN 'api' THEN 'edge-api'
    WHEN 'worker' THEN 'compute'
    WHEN 'all' THEN 'unified'
    ELSE replica_role
END
WHERE replica_profile = 'unified';
