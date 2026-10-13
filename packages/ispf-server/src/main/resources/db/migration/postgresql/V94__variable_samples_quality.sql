-- ADR-0074: nullable historian quality. NULL means unknown / pre-v2, not GOOD.
ALTER TABLE variable_samples
    ADD COLUMN IF NOT EXISTS quality VARCHAR(32);
