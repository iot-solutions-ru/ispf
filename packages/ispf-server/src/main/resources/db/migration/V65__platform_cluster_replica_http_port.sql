ALTER TABLE platform_cluster_replicas
    ADD COLUMN http_port INT;

UPDATE platform_cluster_replicas
SET http_port = 8080
WHERE http_port IS NULL;
