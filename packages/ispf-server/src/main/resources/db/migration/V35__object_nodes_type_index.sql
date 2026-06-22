CREATE INDEX IF NOT EXISTS idx_object_nodes_type ON object_nodes (type);
CREATE INDEX IF NOT EXISTS idx_object_nodes_path_prefix ON object_nodes (path varchar_pattern_ops);
