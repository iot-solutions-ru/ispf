UPDATE oc_lab_sample SET status = COALESCE(NULLIF(?,''), status) WHERE act_no = ?
