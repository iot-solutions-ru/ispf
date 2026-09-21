
SELECT d.doc_id, d.kind, d.status,
       (SELECT COUNT(*) FROM emc_inventory_document_line l WHERE l.doc_id = d.doc_id) AS lines,
       COALESCE(d.operator_person_id, '') AS operator_person_id,
       CAST(d.created_at AS VARCHAR) AS created_at
FROM emc_inventory_document d ORDER BY d.created_at DESC
