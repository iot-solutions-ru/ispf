
SELECT a.asset_id, COALESCE(a.class_id, '') AS class_id, COALESCE(a.equipment_id, '') AS equipment_id,
       COALESCE(a.serial_no, '') AS serial_no, COALESCE(a.manufacturer, '') AS manufacturer,
       COALESCE(a.description, '') AS description, a.status
FROM emc_physical_asset a ORDER BY a.asset_id
