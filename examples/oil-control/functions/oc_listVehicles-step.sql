SELECT vehicle_code, vehicle_type, plate_no, capacity_l, status FROM oc_vehicle WHERE COALESCE(status,'') <> 'archived' ORDER BY vehicle_code
