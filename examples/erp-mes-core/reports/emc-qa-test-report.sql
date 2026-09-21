
SELECT CAST(id AS VARCHAR) AS id, COALESCE(job_no, '') AS job_no,
       COALESCE(lot_id, '') AS lot_id, test_name, result,
       COALESCE(measurements_json, '') AS measurements_json,
       CAST(created_at AS VARCHAR) AS created_at
FROM emc_qa_test_result ORDER BY created_at DESC
