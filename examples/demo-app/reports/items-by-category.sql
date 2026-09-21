SELECT i.item_code, i.status, c.category_code FROM demo_item i JOIN demo_category c ON c.id = i.category_id ORDER BY c.category_code, i.item_code
