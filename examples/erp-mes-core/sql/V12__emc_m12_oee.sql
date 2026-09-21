CREATE TABLE IF NOT EXISTS emc_oee_shift (
       id UUID PRIMARY KEY,
       equipment_id VARCHAR(64) NOT NULL,
       shift_label VARCHAR(64) NOT NULL,
       planned_min NUMERIC(10,1) NOT NULL DEFAULT 480,
       availability_loss_min NUMERIC(10,1) NOT NULL DEFAULT 0,
       performance_loss_min NUMERIC(10,1) NOT NULL DEFAULT 0,
       produced_qty NUMERIC(14,3) NOT NULL DEFAULT 0,
       good_qty NUMERIC(14,3) NOT NULL DEFAULT 0,
       availability_pct NUMERIC(7,3),
       performance_pct NUMERIC(7,3),
       quality_pct NUMERIC(7,3),
       oee_pct NUMERIC(7,3),
       calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
