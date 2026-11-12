UPDATE mes_batch_run SET phase = ?, batch_id = ?, recipe = COALESCE(NULLIF(?, ''), recipe), updated_at = CURRENT_TIMESTAMP WHERE batch_path = ?
