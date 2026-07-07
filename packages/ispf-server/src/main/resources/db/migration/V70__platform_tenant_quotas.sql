ALTER TABLE platform_tenants ADD COLUMN IF NOT EXISTS max_devices INT;
ALTER TABLE platform_tenants ADD COLUMN IF NOT EXISTS max_objects INT;
