INSERT INTO emc_master_data_replica (entity_type, external_id, payload_json) SELECT ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM emc_master_data_replica WHERE entity_type = ? AND external_id = ?)
