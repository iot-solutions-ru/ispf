SELECT directive_id, COALESCE(work_master_id, '') AS work_master_id, COALESCE(job_no, '') AS job_no, title, status FROM emc_work_directive ORDER BY directive_id
