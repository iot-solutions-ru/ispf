SELECT performance_id, COALESCE(job_no, '') AS job_no, COALESCE(work_master_id, '') AS work_master_id, good_qty, reject_qty, status FROM emc_work_performance ORDER BY calculated_at DESC
