INSERT INTO emc_defect_status_history (id, defect_no, from_status, to_status, actor, note) VALUES (gen_random_uuid(), ?, 'REGISTERED', 'REJECTED', ?, NULLIF(?, ''))
