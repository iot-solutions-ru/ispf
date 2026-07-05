ALTER TABLE platform_cluster_replicas
    ADD COLUMN replica_role VARCHAR(16) NOT NULL DEFAULT 'all';
