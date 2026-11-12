-- plant_order_note: fork-specific column (no seed data)
ALTER TABLE demo_item ADD COLUMN IF NOT EXISTS order_note VARCHAR(256);
