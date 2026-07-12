-- Full-variable historian snapshots ($record) may exceed 2 KiB.
ALTER TABLE variable_samples
    ALTER COLUMN value_text TYPE TEXT;
