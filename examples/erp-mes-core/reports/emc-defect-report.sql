
SELECT defect_no, job_no, defect_type_id, qty_declared, severity, status,
       COALESCE(reason_code, '') AS reason_code, COALESCE(created_by, '') AS created_by, created_at
FROM emc_defect_record
ORDER BY created_at DESC
