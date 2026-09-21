INSERT INTO emc_inventory_document (doc_id, kind, status, operator_person_id) SELECT ?, ?, 'DRAFT', ? WHERE NOT EXISTS (SELECT 1 FROM emc_inventory_document WHERE doc_id = ?)
