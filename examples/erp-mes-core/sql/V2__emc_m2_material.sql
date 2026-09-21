CREATE TABLE IF NOT EXISTS emc_material_class (
       class_id VARCHAR(64) PRIMARY KEY,
       description VARCHAR(256),
       parent_class_id VARCHAR(64));
CREATE TABLE IF NOT EXISTS emc_material_definition (
       definition_id VARCHAR(64) PRIMARY KEY,
       class_id VARCHAR(64),
       kind VARCHAR(16) NOT NULL,
       base_uom VARCHAR(16) NOT NULL,
       description VARCHAR(256));
CREATE TABLE IF NOT EXISTS emc_material_lot (
       lot_id VARCHAR(64) PRIMARY KEY,
       barcode VARCHAR(128) NOT NULL UNIQUE,
       definition_id VARCHAR(64) NOT NULL,
       status VARCHAR(32) NOT NULL DEFAULT 'STOCK',
       disposition VARCHAR(32),
       storage_location VARCHAR(64),
       quantity NUMERIC(14,3) NOT NULL DEFAULT 0,
       base_uom VARCHAR(16) NOT NULL DEFAULT 'pcs',
       weight_kg NUMERIC(14,3),
       length_m NUMERIC(14,3),
       on_equipment_id VARCHAR(64),
       on_job_order_id VARCHAR(64),
       external_system VARCHAR(64),
       external_id VARCHAR(128),
       version_no INTEGER NOT NULL DEFAULT 1);
CREATE TABLE IF NOT EXISTS emc_material_sublot (
       sublot_id VARCHAR(64) PRIMARY KEY,
       lot_id VARCHAR(64) NOT NULL,
       barcode VARCHAR(128) NOT NULL UNIQUE,
       status VARCHAR(32) NOT NULL DEFAULT 'STOCK',
       storage_location VARCHAR(64),
       quantity NUMERIC(14,3) NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS emc_material_lot_property (
       lot_id VARCHAR(64) NOT NULL,
       prop_key VARCHAR(64) NOT NULL,
       prop_value VARCHAR(512),
       uom VARCHAR(32));
INSERT INTO emc_material_class (class_id, description, parent_class_id) SELECT 'MCL-RAW', 'Raw materials', NULL WHERE NOT EXISTS (SELECT 1 FROM emc_material_class WHERE class_id = 'MCL-RAW');
INSERT INTO emc_material_class (class_id, description, parent_class_id) SELECT 'MCL-WIP', 'Work in progress', NULL WHERE NOT EXISTS (SELECT 1 FROM emc_material_class WHERE class_id = 'MCL-WIP');
INSERT INTO emc_material_class (class_id, description, parent_class_id) SELECT 'MCL-FG', 'Finished goods', NULL WHERE NOT EXISTS (SELECT 1 FROM emc_material_class WHERE class_id = 'MCL-FG');
INSERT INTO emc_material_definition (definition_id, class_id, kind, base_uom, description) SELECT 'RAW-PLASTIC-GRANULE', 'MCL-RAW', 'RAW', 'kg', 'Plastic granulate' WHERE NOT EXISTS (SELECT 1 FROM emc_material_definition WHERE definition_id = 'RAW-PLASTIC-GRANULE');
INSERT INTO emc_material_definition (definition_id, class_id, kind, base_uom, description) SELECT 'RAW-PACKAGING-BOX', 'MCL-RAW', 'RAW', 'pcs', 'Packaging box' WHERE NOT EXISTS (SELECT 1 FROM emc_material_definition WHERE definition_id = 'RAW-PACKAGING-BOX');
INSERT INTO emc_material_definition (definition_id, class_id, kind, base_uom, description) SELECT 'WIP-HOUSING', 'MCL-WIP', 'WIP', 'pcs', 'Assembled housing' WHERE NOT EXISTS (SELECT 1 FROM emc_material_definition WHERE definition_id = 'WIP-HOUSING');
INSERT INTO emc_material_definition (definition_id, class_id, kind, base_uom, description) SELECT 'FG-UNIT-PACKED', 'MCL-FG', 'FG', 'pcs', 'Packed unit' WHERE NOT EXISTS (SELECT 1 FROM emc_material_definition WHERE definition_id = 'FG-UNIT-PACKED');
INSERT INTO emc_material_lot (lot_id, barcode, definition_id, status, storage_location, quantity, base_uom, weight_kg) SELECT 'LOT-RAW-0001', 'BC-RAW-0001', 'RAW-PLASTIC-GRANULE', 'STOCK', 'WH-LINE-A01', '500', 'kg', '500' WHERE NOT EXISTS (SELECT 1 FROM emc_material_lot WHERE lot_id = 'LOT-RAW-0001');
INSERT INTO emc_material_lot (lot_id, barcode, definition_id, status, storage_location, quantity, base_uom, weight_kg) SELECT 'LOT-RAW-0002', 'BC-RAW-0002', 'RAW-PLASTIC-GRANULE', 'STOCK', 'WH-CENTRAL', '1000', 'kg', '1000' WHERE NOT EXISTS (SELECT 1 FROM emc_material_lot WHERE lot_id = 'LOT-RAW-0002');
INSERT INTO emc_material_lot (lot_id, barcode, definition_id, status, storage_location, quantity, base_uom, weight_kg) SELECT 'LOT-WIP-0001', 'BC-WIP-0001', 'WIP-HOUSING', 'STOCK', 'WH-CENTRAL', '200', 'pcs', NULL WHERE NOT EXISTS (SELECT 1 FROM emc_material_lot WHERE lot_id = 'LOT-WIP-0001');
INSERT INTO emc_material_lot (lot_id, barcode, definition_id, status, storage_location, quantity, base_uom, weight_kg) SELECT 'LOT-FG-0001', 'BC-FG-0001', 'FG-UNIT-PACKED', 'STOCK', 'WH-CENTRAL', '150', 'pcs', NULL WHERE NOT EXISTS (SELECT 1 FROM emc_material_lot WHERE lot_id = 'LOT-FG-0001')
