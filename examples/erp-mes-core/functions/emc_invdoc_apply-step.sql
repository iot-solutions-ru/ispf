UPDATE emc_inventory_document SET status = 'ACCEPTED', completed_at = CURRENT_TIMESTAMP, version_no = version_no + 1 WHERE doc_id = ?
