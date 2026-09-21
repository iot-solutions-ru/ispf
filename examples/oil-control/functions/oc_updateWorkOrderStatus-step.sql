UPDATE oc_work_order SET status = COALESCE(NULLIF(?,''), status), assignee = COALESCE(NULLIF(?,''), assignee) WHERE wo_no = ?
