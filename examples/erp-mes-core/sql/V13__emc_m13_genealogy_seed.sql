INSERT INTO emc_lot_genealogy (id, input_lot_id, output_lot_id, quantity)
       SELECT gen_random_uuid(), 'LOT-RAW-0001', 'LOT-WIP-0001', 120
       WHERE NOT EXISTS (SELECT 1 FROM emc_lot_genealogy
                         WHERE input_lot_id = 'LOT-RAW-0001' AND output_lot_id = 'LOT-WIP-0001');
INSERT INTO emc_lot_genealogy (id, input_lot_id, output_lot_id, quantity)
       SELECT gen_random_uuid(), 'LOT-RAW-0002', 'LOT-WIP-0001', 80
       WHERE NOT EXISTS (SELECT 1 FROM emc_lot_genealogy
                         WHERE input_lot_id = 'LOT-RAW-0002' AND output_lot_id = 'LOT-WIP-0001');
INSERT INTO emc_lot_genealogy (id, input_lot_id, output_lot_id, quantity)
       SELECT gen_random_uuid(), 'LOT-WIP-0001', 'LOT-FG-0001', 150
       WHERE NOT EXISTS (SELECT 1 FROM emc_lot_genealogy
                         WHERE input_lot_id = 'LOT-WIP-0001' AND output_lot_id = 'LOT-FG-0001')
