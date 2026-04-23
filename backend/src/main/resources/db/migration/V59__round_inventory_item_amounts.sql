UPDATE inventory_item
SET amount = ROUND(amount::numeric, 3);
